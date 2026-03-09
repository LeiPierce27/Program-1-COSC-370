
import java.net.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class ICMPPinger {

    //ICMP Constants
    static final int EchoReply = 0;
    static final int DestUnreach = 3;
    static final int EchoRqt = 8;
    static final int TimeExceed = 11;

    static final int TimeoutMS = 1000;
    static final int PayloadSZ = 32;
    static final int DfltCnt = 10;

    static final int MaxRe = 5;


    public static void main( String[] args) throws Exception {

        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String H = args[0];
        int cnt = DfltCnt;
        if (args.length >=2) {
            try {
                System.err.println("Error: Count Must Be A Positive Integer. Using Default (" + DfltCnt + "). ");
                cnt = DfltCnt;
            } catch (NumberFormatException e) {
                System.err.println("Error:Invalid Count \"" + args[1] + "\". Using Default (" + DfltCnt + ").");
                cnt = DfltCnt;
            }
        }


        InetAddress addr;

        try {
            addr = InetAddress.getByName(H);
        } catch (UnknownHostException e) {
            System.err.println("Error: Cannot Resolve Host \"" + H +"\"");
            System.err.println(" Check the Address/Hostname and Your Network Connection!");
            System.exit(1);
            return;
        }

        String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("Started   : " + timeStamp);

        System.out.println("PING " + H + " (" + addr.getHostAddress() + ") " 
        + PayloadSZ + " bytes of data, " + cnt + " packets.\n");

        boolean useRaw = canUseRawSocket(addr);

        if (useRaw) {
            System.out.println("Mode : Raw ICMP Socket >>> Packet Info Available (TTL = N/A)\n");
            runRawPing(addr, H, cnt);
        } else {
            System.out.println("Mode: Fall back (InetAddress.isReachable) >>> (TTL = N/A)\n");
            System.out.println("( Java DatagramSocket Does Not Expose Incoming IP Header)");
            runFallbackPing(addr, H, cnt);
        }

        
    }

    //Raw ICMPPing

    /**
     * Algorithm:
     *     for seq = 1 to count:
     *      build ICMPEchoRequest (type=8, id=pid, seq=seq)
     *      record sendTime; send packet
     *      wait up for 1 sec, reply (retry up to MaxRe times
     *          if not matching packets arrive)
     *      if EchoReply Received: Compute RTT; print reply line
     *      if Type 3 received: print Dest Unreachable error
     *      if Type 11 Received: print Time Exceeded Error
     *      if Timeout: print "Request Timeout"
     *      Sleep 1 Sec
     *     print Summary Stats
     */

    static void runRawPing(InetAddress addr, String H, int cnt) {

        List<Double> rtts = new ArrayList<>();
        int sent = 0, recv = 0;

        int pid = (int)(ProcessHandle.current().pid() & 0xFFFF);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TimeoutMS);

            for(int seq=1; seq <= cnt; seq++) {
                
                byte[] pkt = buildICMPEchoRequest( pid, seq );
                DatagramPacket sendPkt = new DatagramPacket( 
                    pkt, pkt.length, addr, 0
                );
                long sendTime = System.nanoTime();

                try {
                    socket.send( sendPkt );
                    sent++;
                } catch (IOException e) {
                    System.err.println(" Send error (seq = " + seq + "): " + e.getMessage());
                    sleepOneSec();
                    continue;
                }

                int ReCount = 0;
                boolean handled = false;

                while ( !handled && ReCount < MaxRe ) {
                    byte[] recvBuf = new byte[1024];
                    DatagramPacket recvPkt = new DatagramPacket( recvBuf, recvBuf.length);
                    try {
                        
                        socket.receive(recvPkt);
                        long recvTime = System.nanoTime();

                        byte[] raw = new byte[recvPkt.getLength()];
                        System.arraycopy(recvPkt.getData(), 0, raw, 0, raw.length);

                        ICMPPkt icmp = ICMPReply(raw);
                        if ( icmp == null ) {
                            ReCount++;
                            continue;
                        }

                        handled = true;

                        if ( icmp.type == EchoReply && icmp.id == pid ) {
                            double rttMs = ( recvTime - sendTime ) / 1_000_000.0;
                            rtts.add(rttMs);
                            recv++;

                            System.out.printf(
                                "Reply from %-16s  Bytes=%-4d  Seq=%-4d  TTL=N/A  Time=%.2f ms%n",
                                addr.getHostAddress(), icmp.dataLen, seq, rttMs);
                        } else if ( icmp.type == DestUnreach ) {
                            System.out.printf(" Seq=%-4d  ICMP Error -> %s%n",
                            seq, describeDestUnreach(icmp.code));
                        }else if ( icmp.type == TimeExceed ) {
                            System.out.printf("  Seq=%-4d  ICMP Error -> Time Exceed (TTL Expired, Code=%d)%n",
                            seq, icmp.code);
                        } else {
                            System.out.printf("  Seq=%-4d Unexpected ICMP Type=%d Code=%d%n",
                            seq, icmp.type, icmp.code);
                        }
                    } catch ( SocketTimeoutException e ) {
                        System.out.printf("  Request Timeout for Seq=%-4d%n", seq);
                        handled = true;
                    } catch ( IOException e ) {
                        System.err.println("  Receive Error (Seq=" + seq + "): " + e.getMessage());
                        handled = true;
                    }
                }

                if ( !handled ) {
                    System.out.printf(" Request Timeout for Seq=%-4d (Too many Foreign Packets)%n", seq);
                }

                sleepOneSec();
            }
        }catch (SocketException e) {
            System.err.println("Socket Error: " + e.getMessage());
        }

        printSum(H, sent, recv, rtts);
    }

    //Fallback Ping(InetAddress.isReachable)

    /**
     * Used when Raw Socket access is Unavaiable ( no sudo ).
     * InetAddress.isReachable() uses ICMP on Unix Systems and TCP
     * echo on Windows. RTT is accuratly times but TTL is not accessible
     * via Java API.
     * 
     * Algorithm:
     *     For seq = 1 to count
     *      record sendTime
     *      call addr.isReachable(1000)
     *      record recvTime
     *      if reachable: rtt = (recvTime - sendTime)/1,000,000
     *      else: print "Request Timeout"
     *      sleep 1 sec
     *     print Summary Stats
     */
    static void runFallbackPing( InetAddress addr, String H, int cnt ) {

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

                    if ( reachable ) {
                        double rttMs = (recvTime - sendTime) / 1_000_000.0;
                        rtts.add(rttMs);
                        recv++;
                        System.out.printf(
                            "Reply from %-16s  Bytes=%-4d  Seq=%-4d TTL=N/A  Time=%.2f ms%n",
                            addr.getHostAddress(), PayloadSZ, seq, rttMs);
                    } else {
                        System.out.printf("  Request Timeout for Seq=%-4d%n", seq);
                    }

                    sleepOneSec();
                }

                printSum(H, sent, recv, rtts);
            }

            //ICMP EchoRequest Builder

            static byte[] buildICMPEchoRequest ( int pid, int seq ) {
                int HeadLen = 8;
                int TotLen = HeadLen + PayloadSZ;
                ByteBuffer buf = ByteBuffer.allocate(TotLen);

                buf.put((byte) EchoRqt);
                buf.put((byte) 0);
                buf.putShort((short) 0);
                buf.putShort((short) (pid & 0xFFFF));
                buf.putShort((short) (seq & 0xFFFF));

                buf.putLong(System.currentTimeMillis());
                for (int i = HeadLen + 8; i < TotLen; i++) {
                    buf.put((byte)(i & 0xFF));
                }

                byte[] pkt = buf.array();
                int checkSum = ComputeCheckSum(pkt);
                pkt[2] = (byte)((checkSum >> 8) & 0xFF);
                pkt[3] = (byte)( checkSum & 0xFF);

                return pkt;
            }

            //Internet CheckSum

            /**
             * Algorithm:
             *      Sum all 16 bit words in the data
             *      Handles odd trailing bytes
             *      Folding any carry bits
             *      Returns one's complement
             */
            static int ComputeCheckSum( byte[] data ) {
                int sum = 0;
                int len = data.length;
                int i = 0;

                while (len > 1) {
                    sum += ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
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


            //ICMP Pkt Container
            static class ICMPPkt {
                int type, code, id, seq, dataLen;
                //No TTL Field Due To Java DatagramScoekt
                //Not Exposing Incoming IP Header Fields
                //Cant Read from Received Packets
            }

            //ICMP Reply Parser
            /**
             * Java's DagagramSocket.receive() returns only the
             * ICMP Portion, no IP Header. The Buffer maps Directly so:
             */
            static ICMPPkt ICMPReply( byte[] raw ) {
                if ( raw == null || raw.length < 8) return null;

                ICMPPkt p = new ICMPPkt();
                p.type = raw[0] & 0xFF;
                p.code = raw[1] & 0xFF;
                p.id = ((raw[4] & 0xFF) << 8) | (raw[5] & 0xFF);
                p.seq = ((raw[6] & 0xFF) << 8) | (raw[7] & 0xFF);
                p.dataLen = raw.length - 8;

                return p;
            }


            //Extra Credit

            /**
             * Maps ICMP Destination Unreachable Sub-Codes
             */
            static String describeDestUnreach(int code){
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

        static void printSum(String H, int sent, int recv, List<Double> rtts) {
            int lost = sent - recv;
            double lossPkt = (sent > 0) ? (lost * 100.0 / sent) : 100.0;

            System.out.println("\n--- " + H + " Ping Stats ---");
            System.out.printf(
                "%d Packet(s) Transmitted, %d Received, %d Lost (%.0f%% Packet Loss)%n",
                sent, recv, lost, lossPkt);
            
            if (!rtts.isEmpty()) {
                double min = rtts.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
                double max = rtts.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
                double avg = rtts.stream().mapToDouble(Double::doubleValue).average().getAsDouble();

                double variance = rtts.stream().mapToDouble(r -> (r - avg) * (r - avg)).average().getAsDouble();
                double StdDev = Math.sqrt(variance);

                System.out.printf("RTT Min=%.2f ms   Avg=%.2f ms   Max=%.2f ms   StdDev=%.2f ms%n%n.", min, avg, max, StdDev);
            } else {
                System.out.println("No Replies Received... Unavailable RTT Statistics!");
            }
        }

        //Helpers

        /*
        *Tests if the Raw ICMP Socket Access is Available by
        *Performing a Loopback Send + Receive probe on LocalHost
        *With 500ms Timeout. Returns true only if an Actual 
        *ICMP Echo Reply Comes Back Matching
        */
        static boolean canUseRawSocket(InetAddress addr) {
            InetAddress Loopback;
            try {
                Loopback = InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException e) {
                return false;
            }

            try (DatagramSocket s = new DatagramSocket()) {
                s.setSoTimeout(500);

                int probePid = 0xCAFE;
                byte[] probe = buildICMPEchoRequest(probePid, 0);
                s.send(new DatagramPacket(probe, probe.length, Loopback, 0));

                byte[] buf = new byte[256];
                DatagramPacket resp = new DatagramPacket(buf, buf.length);
                s.receive(resp);



                byte[] raw = new byte[resp.getLength()];
                System.arraycopy(resp.getData(), 0, raw, 0, raw.length);
                ICMPPkt icmp = ICMPReply(raw);

                return icmp != null && icmp.type == EchoReply && icmp.id == probePid;
            } catch (Exception e){
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
