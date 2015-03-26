import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class GameInstance extends Thread {
  public GameInstance(ArrayList<String> programs) {
    this.programs = programs;
    messages = new LinkedBlockingQueue<Message>();
    processes = new ArrayList<Process>();
    input_all = new HashMap<Integer, PrintWriter>();
    input_master = new ArrayList<Integer>();
    input_rest = new ArrayList<Integer>();
    names = new HashMap<Integer, String>();
  }

  public void handleMessage(Message m) {
    System.out.println(m);

    // Ignore things sent to stderr
    if (m.type == StreamType.stderr)
      return;

    // Handle things if it is a server command
    if (handleServerCommand(m))
      return;

    // Send message to appropriate threads
    if (m.broadcast)
      postAll(m);
    else
      postMasters(m);
  }

  private boolean handleServerCommand(Message m) {
    // All server commands start with a #
    if (m.message.length() == 0)
      return false;
    if (m.message.charAt(0) != '#')
      return false;

    // We never care about trailing white space
    m.message = m.message.trim();

    // Process the server command
    if (m.message.startsWith("#getname")) {
      // Get name of specified player
      if (m.message.length() < 10) {
        System.err.println("Malformed #getname query from " + names.get(m.id) +
                           ". Proper syntax is '#getname ID'");
      } else {
        try {
          int query = Integer.parseInt(m.message.substring(9));
          System.out.println("Received #getname query from " + names.get(m.id) +
                             "; answer: " + names.get(query));
          reply(m, "#getname " + query + " " + names.get(query));
        } catch (NumberFormatException e) {
          System.err.println("Malformed #getname query from " +
                             names.get(m.id) +
                             ". ID was not parsable as a number. "
                             + "Proper syntax is '#getname ID'");
        } catch (ArrayIndexOutOfBoundsException e) {
          System.err.println("Malformed #getname query from " +
                             names.get(m.id) + ". ID is not valid. "
                             + "Proper syntax is '#getname ID'");
        }
      }
    } else if (m.message.startsWith("#name")) {
      // Set name for a client
      if (m.message.length() < 7) {
        System.err.println("Malformed #name command from " + names.get(m.id) +
                           ". No name specified. "
                           + "Proper syntax is '#name NAME'");
      } else {
        names.put(m.id, m.message.substring(6));
      }
    } else if (m.message.equals("#players")) {
      // Query number of players
      System.out.println("Received #players query from " + names.get(m.id) +
                         "; answer: " + names.size());
      reply(m, "#players " + names.size());
    } else if (m.message.equals("#quit")) {
      // Quit the game
      running = false;
      postAll(new Message("#quit"));
    }
    return true;
  }

  public void reply(Message m, String reply) {
    input_all.get(m.id).println(reply);
  }

  public void postAll(Message m) {
    for (Map.Entry<Integer, PrintWriter> me : input_all.entrySet())
      me.getValue().println(m.message);
  }

  public void postMasters(Message m) {
    for (Integer i : input_master)
      input_all.get(i).println(m.id + " " + m.message);
  }

  public void processMessages() {
    Message m;

    running = true;
  main:
    while (running) {
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
      }
      // Check for new message
      m = messages.poll();

      if (m != null)
        handleMessage(m);

      // Check if any processes have died
      // If they are still running, exitValue triggers an exception
      for (int i = 0; i < programs.size(); ++i) {
        // for (Process p : processes) {
        try {
          processes.get(i).exitValue();
          System.err.println("Player '" + names.get(i) + "' has quit. cmd: '" +
                             programs.get(i) + "'");
          break main;
        } catch (IllegalThreadStateException e) {
        }
      }
    }

    // Handle any remaining messages
    while ((m = messages.poll()) != null) {
      handleMessage(m);
    }

    // Forcibly kill any remaining running processes
    for (Process p : processes) {
      try {
        p.exitValue();
      } catch (IllegalThreadStateException e) {
        p.destroy();
      }
    }
  }

  public void run() {
    System.out.println("Will start game run by: '" + programs.get(0) + "'");
    System.out.println("Player1: '" + programs.get(1) + "'");
    System.out.println("Player2: '" + programs.get(2) + "'");

    // Start processes
    startProcess(programs.get(0), true, 0); // Master broadcasts

    for (int i = 1; i < programs.size(); ++i)
      startProcess(programs.get(i), false, i);

    processMessages();
  }

  private void startProcess(String commandline, boolean broadcast, int id) {
    try {
      ArrayList<String> args = new ArrayList<String>();
      for (String s : commandline.split(" "))
        args.add(s);
      ProcessBuilder pb = new ProcessBuilder(args);
      Process p = pb.start();
      processes.add(p);

      StreamConsumer stdout = new StreamConsumer(
          p.getInputStream(), StreamType.stdout, broadcast, messages, id);
      StreamConsumer stderr = new StreamConsumer(
          p.getErrorStream(), StreamType.stderr, broadcast, messages, id);
      stdout.start();
      stderr.start();

      PrintWriter stdin = new PrintWriter(
          new OutputStreamWriter(new BufferedOutputStream(p.getOutputStream())),
          true);
      input_all.put(id, stdin);
      if (broadcast)
        input_master.add(id);
      else
        input_rest.add(id);
    } catch (IOException e) {
      System.err.println("IO Exception when running '" + commandline + "'");
      System.err.println(e);
    }
  }

  private HashMap<Integer, PrintWriter> input_all;
  private ArrayList<Integer> input_master;
  private ArrayList<Integer> input_rest;
  private LinkedBlockingQueue<Message> messages;
  private HashMap<Integer, String> names;
  private ArrayList<Process> processes;
  private ArrayList<String> programs;
  private boolean running;
}
