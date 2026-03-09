import java.net.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ICMPPinger {

  // ICMP Constants 
  static final int EchoReply   = 0;
  static final int DestUnreach = 3;
  static final int EchoRequest = 8;
  static final int TimeExceed  = 11;

  static final int TimeoutMS   = 1000;
  static final int PayloadSize = 32;
  static final int DfltCnt     = 10;


  static final int MAX_RETRIES = 5;

  public static void main(String[] args) throws Exception {

    if (args.length < 1) {
      printUsage();
      System.exit(1);
    }

    String host = args[0];
    int cnt = (args.length >= 2) ? Integer.parseInt(args[1]) : DfltCnt;

    InetAddress addr;
    try {
      addr = InetAddress.getByName(host);
    } catch (UnknownHostException e) {
      System.err.println("Error: Cannot Resolve Host \"" + host + "\"");
      System.err.println("  Check the address/hostname and your network connection.");
      System.exit(1);
      return;
    }

    System.out.println("PING " + host + " (" + addr.getHostAddress() + ") "
                       + PayloadSize + " bytes of data, " + cnt + " packets.\n");

  
    boolean useRaw = canUseRawSocket(addr);

    if (useRaw) {
      System.out.println("Mode: Raw ICMP Socket ... Packet Info Available (TTL = N/A)\n");
      runRawPing(addr, host, cnt);
    } else {
      System.out.println("Mode: Fallback (InetAddress.isReachable) -- TTL Not Available");
      System.out.println("( Java DatagramSocket Does Not Expose Incoming IP Header.  )\n");
      runFallbackPing(addr, host, cnt);
    }
  }


  //Raw ICMP Ping

  static void runRawPing(InetAddress addr, String host, int cnt) {
    List<Double> rtts = new ArrayList<>();
    int sent = 0, recv = 0;
    int pid = (int)(ProcessHandle.current().pid() & 0xFFFF);

    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setSoTimeout(TimeoutMS);

      for (int seq = 1; seq <= cnt; seq++) {

        byte[] packet = buildICMPEchoRequest(pid, seq);
        DatagramPacket sendPkt = new DatagramPacket(
          packet, packet.length, addr, 0);
        long sendTime = System.nanoTime();

        try {
          socket.send(sendPkt);
          sent++;
        } catch (IOException e) {
          System.err.println("  Send error (seq=" + seq + "): " + e.getMessage());
          sleepOneSec();
          continue;
        }

  
        int retryCount = 0;
        boolean handled = false;

        while (!handled && retryCount < MAX_RETRIES) {
          byte[] recvBuf = new byte[1024];
          DatagramPacket recvPkt = new DatagramPacket(recvBuf, recvBuf.length);
          try {
            socket.receive(recvPkt);
            long recvTime = System.nanoTime();

            byte[] raw = new byte[recvPkt.getLength()];
            System.arraycopy(recvPkt.getData(), 0, raw, 0, raw.length);

            ICMPPacket icmp = parseICMPReply(raw);
            if (icmp == null) {
              retryCount++;
              continue;
            }

            handled = true;

            if (icmp.type == EchoReply && icmp.id == pid) {
              double rttMs = (recvTime - sendTime) / 1_000_000.0;
              rtts.add(rttMs);
              recv++;
              //TTL shown as N/A — DatagramSocket does not expose incoming IP header
              System.out.printf(
                "Reply from %-16s    bytes=%-4d   seq=%-4d   TTL=N/A   time=%.2f ms%n",
                addr.getHostAddress(),
                icmp.dataLen, seq, rttMs);

           
            } else if (icmp.type == DestUnreach) {
              System.out.printf("  seq=%-4d   ICMP Error  ->  %s%n",
                                seq, describeDestUnreachable(icmp.code));

            } else if (icmp.type == TimeExceed) {
              System.out.printf(
                "  seq=%-4d   ICMP Error  ->  Time Exceeded (TTL expired, code=%d)%n",
                seq, icmp.code);

            } else {
              System.out.printf("  seq=%-4d   Unexpected ICMP type=%d code=%d%n",
                                seq, icmp.type, icmp.code);
            }

          } catch (SocketTimeoutException e) {
            System.out.printf("  Request timeout for seq=%-4d%n", seq);
            handled = true; //timeout counts as handled — move to next seq
          } catch (IOException e) {
            System.err.println("  Receive error (seq=" + seq + "): " + e.getMessage());
            handled = true;
          }
        }

        //If we exhausted retries without a usable packet, report timeout
        if (!handled) {
          System.out.printf("  Request timeout for seq=%-4d (too many foreign packets)%n", seq);
        }

        sleepOneSec();
      }

    } catch (SocketException e) {
      System.err.println("Socket Error: " + e.getMessage());
    }

    printSummary(host, sent, recv, rtts);
  }


  //Fallback Ping (InetAddress.isReachable)

  static void runFallbackPing(InetAddress addr, String host, int cnt) {
    List<Double> rtts = new ArrayList<>();
    int sent = 0, recv = 0;

    for (int seq = 1; seq <= cnt; seq++) {
      sent++;
      long sendTime = System.nanoTime();
      boolean reachable;
      try {
        reachable = addr.isReachable(TimeoutMS);
      } catch (IOException e) {
        reachable = false;
      }
      long recvTime = System.nanoTime();

      if (reachable) {
        double rttMs = (recvTime - sendTime) / 1_000_000.0;
        rtts.add(rttMs);
        recv++;
        System.out.printf(
          "Reply from %-16s   bytes=%-4d  seq=%-4d   TTL=N/A   time=%.2f ms%n",
          addr.getHostAddress(), PayloadSize, seq, rttMs);
      } else {
        System.out.printf("   Request Timeout for seq=%-4d%n", seq);
      }

      sleepOneSec();
    }

    printSummary(host, sent, recv, rtts);
  }


  //ICMP Echo Request Builder

  static byte[] buildICMPEchoRequest(int pid, int seq) {
    int HeadLen = 8;
    int totLen  = HeadLen + PayloadSize;
    ByteBuffer buf = ByteBuffer.allocate(totLen);

    buf.put((byte) EchoRequest);         //Type = 8
    buf.put((byte) 0);                   //Code = 0
    buf.putShort((short) 0);             //Checksum placeholder
    buf.putShort((short)(pid & 0xFFFF)); //Identifier
    buf.putShort((short)(seq & 0xFFFF)); //Sequence number

    buf.putLong(System.currentTimeMillis()); // Timestamp (8 bytes)
    for (int i = HeadLen + 8; i < totLen; i++) {
      buf.put((byte)(i & 0xFF));         // Padding bytes
    }

    byte[] packet = buf.array();
    int checkSum = computeChecksum(packet);
    packet[2] = (byte)((checkSum >> 8) & 0xFF);
    packet[3] = (byte)( checkSum       & 0xFF);

    return packet;
  }


  //Internet Checksum 

  static int computeChecksum(byte[] data) {
    int sum = 0;
    int len = data.length;
    int i   = 0;

    while (len > 1) {
      sum += ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
      i   += 2;
      len -= 2;
    }
    if (len == 1) {
      sum += (data[i] & 0xFF) << 8;  //Odd leftover byte
    }
    while ((sum >> 16) != 0) {
      sum = (sum & 0xFFFF) + (sum >> 16);  //Fold carry bits
    }
    return (~sum) & 0xFFFF;
  }


  //ICMP Packet Container

  static class ICMPPacket {
    int type, code, id, seq, dataLen;
    //NOTE: TTL field removed, Java DatagramSocket does not expose incomin IP header fields, so TTL can't be read from received packets.
  }


  //ICMP Reply Parser
  static ICMPPacket parseICMPReply(byte[] raw) {
    if (raw == null || raw.length < 8) return null; 

    ICMPPacket p = new ICMPPacket();
    p.type    =  raw[0] & 0xFF;                             
    p.code    =  raw[1] & 0xFF;                              
    p.id      = ((raw[4] & 0xFF) << 8) | (raw[5] & 0xFF);   
    p.seq     = ((raw[6] & 0xFF) << 8) | (raw[7] & 0xFF);   
    p.dataLen =   raw.length - 8;                            

    return p;
  }


  //Extra Credit
  static String describeDestUnreachable(int code) {
    switch (code) {
      case  0: return "Destination Network Unreachable";
      case  1: return "Destination Host Unreachable";
      case  2: return "Destination Protocol Unreachable";
      case  3: return "Destination Port Unreachable";
      case  4: return "Fragmentation Required, DF Flag Set";
      case  5: return "Source Route Failed";
      case  6: return "Destination Network Unknown";
      case  7: return "Destination Host Unknown";
      case  8: return "Source Host Isolated";
      case  9: return "Network Administratively Prohibited";
      case 10: return "Host Administratively Prohibited";
      case 11: return "Network Unreachable for TOS";
      case 12: return "Host Unreachable for TOS";
      case 13: return "Communication Administratively Prohibited";
      default: return "Destination Unreachable (code=" + code + ")";
    }
  }



  static void printSummary(String host, int sent, int recv, List<Double> rtts) {
    int    lost    = sent - recv;
    double lossPkt = (sent > 0) ? (lost * 100.0 / sent) : 100.0;

    System.out.println("\n--- " + host + " ping stats ---");
    System.out.printf(
      "%d packet(s) transmitted, %d received, %d lost (%.0f%% packet loss)%n",
      sent, recv, lost, lossPkt);

    if (!rtts.isEmpty()) {
      double min = rtts.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
      double max = rtts.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
      double avg = rtts.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
      System.out.printf("RTT  min=%.2f ms   avg=%.2f ms   max=%.2f ms%n", min, avg, max);
    } else {
      System.out.println("No Replies Received... Unavailable RTT Stats.");
    }
  }


  //Helpers 
  static boolean canUseRawSocket(InetAddress addr) {
    InetAddress loopback;
    try {
      loopback = InetAddress.getByName("127.0.0.1");
    } catch (UnknownHostException e) {
      return false;
    }

    try (DatagramSocket s = new DatagramSocket()) {
      s.setSoTimeout(500);

      int probePid = 0xCAFE;
      byte[] probe = buildICMPEchoRequest(probePid, 0);
      s.send(new DatagramPacket(probe, probe.length, loopback, 0));

      byte[] buf = new byte[256];
      DatagramPacket resp = new DatagramPacket(buf, buf.length);
      s.receive(resp); 
      //throws SocketTimeoutException if there is no ICMP reply arrives


      
      //Verify the reply is a real ICMP Echo Reply (type 0) from our probe
      byte[] raw = new byte[resp.getLength()];
      System.arraycopy(resp.getData(), 0, raw, 0, raw.length);
      ICMPPacket icmp = parseICMPReply(raw);
      return icmp != null && icmp.type == EchoReply && icmp.id == probePid;

    } catch (Exception e) {
      return false;
    }
  }


  static void sleepOneSec() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }


  static void printUsage() {
    System.out.println("Usage: ");
    System.out.println("  Step 1 - Compile:  javac ICMPPinger.java");
    System.out.println("  Step 2 - Run:      sudo java ICMPPinger <host> [count]");
    System.out.println("\nArguments: ");
    System.out.println("  host   IP Address or Hostname ( required )");
    System.out.println("  count  Number of Pings to Send (optional, default=" + DfltCnt + ")");
    System.out.println("\nExamples: ");
    System.out.println("  sudo java ICMPPinger 127.0.0.1");
    System.out.println("  sudo java ICMPPinger google.com 5");
    System.out.println("  sudo java ICMPPinger 8.8.8.8 10");
  }
}
