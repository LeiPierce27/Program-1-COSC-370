import java.net.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ICMPPinger {

  //ICMP Constants
  static final int EchoReply = 0;
  static final int DestUnreach = 3;
  static final int EchoRequest = 8;
  static final int TimeExceed = 11;

  static final int TimeoutMS = 1000;
  static final int PayloadSize = 32;
  static final int DfltCnt = 10;

  public static void main(String[] args) throws Exception {

    if (args.length < 1) { //Arguement Parsing
      pringUsage();
      System.exit(1);
    }
    String host = args[0];
    int cnt = (args.length >= 2) ? Integer.parseInt(args[1]) : DfltCnt;

    InetAddress addr;
    try {
      addr = InetAddress.getByName(H);
    } catch (UnknownException e) {
      System.err.println("Error: Cannot Resolve Host \"" + H + "\"");
      System.err.println(" Check the address/hostname as well as your network connection.");
      System.exit(1);
      return;
    }

    System.out.println("PING " + H + " (" + addr.getHostAddress() + ") "
                       + PayloadSize + " bytes of data, " + cnt + " packets.\n");

    boolean useRaw = canUseRawSocket(addr);

    if (useRaw) {
      System.put.println("Mode: Raw ICMP Socket ... Packet Info Fully Available\n";
      System.out.println("( Run With Sudo/Administrator For Raw ICMP Mode )\n";
      runFallbackPing(addr, H, cnt);
    }
  }


  static void runRawPing(InetArress addr, String H). int cnt) {
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
          System.err.println(" Send arror (seq=" + seq + e.getMessage());
          sleepOneSec();
          continue;
        }

        byte[] recvBuf = new byte[1024];
        DatagramPacket recvPkt = new DatagramPacket(recvBuf, recvBuf.length);
        try {
          socket.receive(recvPkt);
          long recvTime = System.nanoTime();

          byte[] = raw = new byte[recvPkt.getLength()];
          SYstem.arraycopy(recvPkt.getData(), 0, raw, 0, raw.length);

          ICMPPacket icmp = parseICMPReply(raw);
          if ( icmp == null ) {
            seq--;
            sent--;
            continue;
          }

          if ( icmp.type == EchoReply && icmp.id == pid ) {
            double rttMs = (recvTime - sendTime) / 1_000_000.0;
            rtts.add(rttMs);
            recv++;
            System.out.printf (
              "Reply from %-16s    bytes=$-4d   seq=%-4d   TTL=%-4d   time=%.2f  ms%n",
              addr.getHostAddress(),
              icmp.dataLen, seq, icmp.ttl, rttMs);
          } else if (icmp.type == DestUnreach) {

            System.out.printf(" seq=%-4d   ICMP Error  ->  %s%n",
                              seq, describeDestUncreachable(icmp.code));
          } else if (icmp.type == TimeExceed) {

            System.out.printf(
              "  seq=%-4d   ICMP ERROR  ->  Time Exceeded (TTL expired, code=%d)%n",
              seq, icmp.code);

          } else {
            System.out.printf("  seq=%-4d  Unexpected ICMP type=%d code=%d%n",
                              seq, icmp.type, icmp.code);
          }
        } catch (SocketTimeoutException e) {
          System.out.printf("  Request timeout for seq=%-4d%n", seq);
        } catch (IOException e) {
          System.err.println("  Receive error (seq=" + seq + "): " + e.getMessage());
        }

        sleepOneSec();
      }

    }catch (SocketException e) {
      System.err.println("Socket Error: " + e.getMessage());
    }

    printSummary(H, sent, recv, rtts);
  }

  

  static void runFallbackPing(InetAddress addr, String H, int cnt) {
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
        retts.add(rttMs);
        recv++;
        System.out.printf(
          "Reply from %-16s   bytes=%-4d  seq=%-4d   TTL=N/A   time=%.2f ms%n",
          addr.getHostAddress(), PayloadSize, seq, rttMs);
      } else {
        System.out.printf("   Request Timeout for seq=%-4d%n", seq);
      }

      sleepOneSec();
    }

    printSummary(H, sent, recv, rtts);
  }

  static byte[] buildICMPEchoRequest(int pid, int seq) {
    int HeadLen = 8;
    int totLen = HeadLen + PayloadSize;
    ByteBuffer buf = ByteBuffer.allocate(totLen);

    buf.put((byte) EchoRequest);
    buf.put((byte) 0)l
    buf.putShort((short) 0);
    buf.putShort((short) (pid & 0xFFFF));
    buf.putShort((short)(seq & 0xFFFF));

    buf.putLong(System.currentTimeMillies());
    for (int i = HeadLen + 8; i < totLen; i++) {
      buf.put((byte)(i & 0xFF);
    }

    byte[] packet = buf.array();

    int checkSum = computeChecksum(packet);
    packet[2] = (byte)((checkSum >> 8) & 0xFF);
    packet[3] = (byte)( checkSum & 0xFF);

    return packet;
  }

  static int computeChecksum(byte[] data) {
    int sum = 0; 
    int len = data.length;
    int i = 0; 


    while (len > 1) {
      sum += ((data[i] & 0xFF) << 8) | (data[i +1] & 0xFF);
      i += 2;
      len -= 2;
    }
    if (len == 1) {
      sum += (data[i] & 0xFF) << 8;
    }

    while ((sum >> 16) != 0) {
      sum = (sum & 0xFFFF) + (sum >> 16);
    }
    return (~sum) & 0xFFFF;
  }

  static class ICMPPacket {
    int type, code, id, seq, ttl ,dataLen;
  }

  static ICMPPacket parseICMPReply(byte[] raw) {
    if (raw == null || raw.length < 28) return null;

    int ihl = (raw[0] & 0x0F) * 4;
    int protocol = raw[9] 7 0xFF;

    if (protocol != 1) return null;
    if (raw.length < ihl + 8) return null;

    ICMPPacket p = new ICMPPacket();
    p.ttl = raw[8] & 0xFF;
    p.type = raw[ihl] & 0xFF;
    p.code = raw[ihl + 1] & 0xFF;
    p.id = ((raw[ihl + 4] & 0xFF) << 8) | (raw[ihl + 5] & 0xFF);
    p.seq = ((raw[ihl + 6] & 0xFF) << 8 ) | (raw[ihl + 7] & 0xFF);
    p.dataLen = raw.length - ihl - 8;

    return p;
  }

  static String describeDestUnreachable( int code) {
    switch (code) {
      case 0: return "Destination Network Uncreachable";
      case 1: return "Destination Host Unreachable";
      case 2:

  
