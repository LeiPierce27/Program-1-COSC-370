# ICMP Pinger - Java Implementation
### COSC 370 | Program 1 | Socket Programming

---

## Table of Contents
1. [Project Overview](#project-overview)
2. [Supported Platforms](#supported-platforms)
3. [Prerequisites](#prerequisites)
4. [How to Compile/ How to Run](#how-to-compile--how-to-Run)
5. [Sample Output](#sample-output)
6. [Limitations](#limitiations)
7. [References](#references)

---

## Project Overview
This program implements a Ping Application in Java using ICMP (Internet Control Message Protocol) echo request and reply messages. The pinger sends ICMP echo requests to a target host once per second and listens for ICMP echo replies. It measures the Round-Trip Time (RTT) for each packet and reports:

- Per-packet RTT (in milliseconds)
- TTL (Time to Live) of each reply
- Packet Loss Detection (timeout after 1 second)
- Summary Statistics: **Minimum, Maximum, and Average RTT**

---

## Supported Platforms

| Platform      | Supported | Notes |

| **Linux**     |    Yes    |  Reccomened. Run with 'sudo' for raw socket access.     |
| **MacOS**     |    Yes    | Run with 'sudo'. Raw sockets require root on MacOS.     |
| **Win10/11**  |   Limited | Raw socket support is restricted. Must run Admin. Results may vary depending on Windows Security policy.   |

**Important:** This program uses raw sockets, whcih require Admin or Root Privileges on all platforms. Without elevated permissions, the program will throw a scoket permission error and will not run. 

---

## Prerequisites

Before running the program, ensure the following are installed on your system:

| Requirement       | Minimum Version | How to Check     |
| Java JDK          |     JDK 8+      | 'java -version'  |
| javac (compilter) |     JDK 8+      | 'javac -version' |

To Install Java:
- **Linux (Ubuntu/Debian):** 'sudo apt install default-jdk'
- **MacOS:** Download from from [https://www.oracle.com/java/technologies/downloads/](https://www.oracle.com/java/technologies/downloads/) or use Homerbew: 'brew install openjdk'
- **Windows:** Download and install JDK from Oracle's website above.

---

## How to Compile

Open a terminal (or Command Prompt on Windows) and navigate to the project directory.

### Step 1 - Navigate to the Source folder

'''bach
cd ICMPPinger/src
'''

This will generate 'ICMPPinger.class' in the same directory.


### How to Run  / Test Program

Basic Usage:


### Test 1



### Test 2




## Sample Outputs


## Extra Credit



## Limitations

- Raw socket access is blocked or restricted in some managed network enviornments (like univeresity firewalls). If pings to external hosts fail, try a different network.  
- on Windows, raw ICMP sockets behave differently due to OS-level restrictions and may not produce consistent results.
- The program sends one ping per second and waits up to 1 second for a reply before marking a packet as lost.
- IPv4 Only.

---

## References

- Internet Control Message Protocol: https://datatracker.ietf.org/doc/html/rfc792
- Computing the Internet CheckSum: https://datatracker.ietf.org/doc/html/rfc1071
- Java InetAddress API Documentation: https://docs.oracle.com/javase/8/docs/api/java/net/InetAddress.html
- Java DatagramSocket API Documentation: https://docs.oracle.com/javase/8/docs/api/java/net/DatagramSocket.html
