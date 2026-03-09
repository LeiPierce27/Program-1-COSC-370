# ICMP Pinger - Java Implementation
### COSC 370 | Program 1 | Socket Programming

---

## Table of Contents
1. [Project Overview](#project-overview)
2. [Supported Platforms](#supported-platforms)
3. [Prerequisites](#prerequisites)
4. [How to Compile / How to Run](#how-to-compile--how-to-run)
5. [Sample Output](#sample-output)
6. [Extra Credit](#extra-credit)
7. [Limitations](#limitations)
8. [References](#references)

---

## Project Overview
This program implements a Ping Application in Java using ICMP (Internet Control Message Protocol) echo request and reply messages. The pinger sends ICMP echo requests to a target host once per second and listens for ICMP echo replies. It measures the Round-Trip Time (RTT) for each packet and reports:

- Per-packet RTT (in milliseconds)
- TTL (Time to Live) of each reply
- Packet Loss Detection (timeout after 1 second)
- Summary Statistics: **Minimum, Maximum, and Average RTT**

---

## Supported Platforms

| Platform     | Supported | Notes                                                                                             |
|--------------|-----------|---------------------------------------------------------------------------------------------------|
| Linux        | Yes       | Recommended. Run with `sudo` for raw socket access.                                               |
| macOS        | Yes       | Run with `sudo`. Raw sockets require root on macOS.                                               |
| Win10/11     | Limited   | Raw socket support is restricted. Must run as Administrator. Results may vary by security policy. |

> **Important:** This program uses raw sockets, which require **Administrator or Root privileges** on all platforms. Without elevated permissions, the program will throw a socket permission error and will not run.

---

## Prerequisites

Before running the program, ensure the following are installed on your system:

| Requirement       | Minimum Version | How to Check     |
|-------------------|-----------------|------------------|
| Java JDK          | JDK 8+          | `java -version`  |
| javac (compiler)  | JDK 8+          | `javac -version` |

**To Install Java:**

- **Linux (Ubuntu/Debian):**
  ```bash
  sudo apt install default-jdk
  ```
- **macOS:** Download from [https://www.oracle.com/java/technologies/downloads/](https://www.oracle.com/java/technologies/downloads/) or use Homebrew:
  ```bash
  brew install openjdk
  ```
- **Windows:** Download and install the JDK from Oracle's website linked above.

---

## How to Compile / How to Run

Open a terminal (or Command Prompt on Windows) and navigate to the project directory.

### Step 1 — Navigate to the Source Folder

```bash
cd ICMPPinger/src
```

### Step 2 — Compile

```bash
javac ICMPPinger.java
```

This will generate `ICMPPinger.class` in the same directory.

---

### How to Run / Test Program

#### Linux / macOS Terminal

```bash
sudo java ICMPPinger <hostname or IP>
```

Example:

```bash
sudo java ICMPPinger google.com
```

```bash
sudo java ICMPPinger 127.0.0.1
```

#### Windows Command Prompt (run as Administrator)

```bash
java ICMPPinger <hostname or IP>
```

Example:

```bash
java ICMPPinger google.com
```

```bash
java ICMPPinger 127.0.0.1
```

#### Eclipse IDE

1. Import the project: **File → Import → Existing Projects into Workspace**
2. Right-click `ICMPPinger.java` → **Run As → Run Configurations**
3. Under **Arguments**, enter the target hostname or IP (e.g., `127.0.0.1`)
4. On Linux/macOS, Eclipse must be launched with `sudo` for raw socket access

---

## Sample Output

### Test 1 — Localhost (127.0.0.1)

<img width="772" height="287" alt="Localhost ping output" src="https://github.com/user-attachments/assets/59cce8b3-c86c-48f7-8c3a-ecdd340dc951" />

### Test 2 — External Host

<img width="782" height="302" alt="External host ping output" src="https://github.com/user-attachments/assets/4fef6525-4e36-475d-a266-5efde4ab60d1" />

---

## Extra Credit

This implementation parses ICMP error response codes and displays human-readable error messages to the user.

**Supported ICMP error types:**

| ICMP Type | Code | Description                               |
|-----------|------|-------------------------------------------|
| 3         | 0    | Destination Network Unreachable           |
| 3         | 1    | Destination Host Unreachable              |
| 3         | 2    | Destination Protocol Unreachable          |
| 3         | 3    | Destination Port Unreachable              |
| 3         | 9    | Network Administratively Prohibited       |
| 3         | 13   | Communication Administratively Prohibited |
| 11        | 0    | Time Exceeded (TTL expired in transit)    |

Example error output:
```
  seq=3    ICMP Error -> Destination Host Unreachable
  seq=7    ICMP Error -> Time Exceeded (TTL expired, code=0)
```

---

## Limitations

- Raw socket access is blocked or restricted in some managed network environments (e.g., university firewalls). If pings to external hosts fail, try a different network.
- On Windows, raw ICMP sockets behave differently due to OS-level restrictions and may not produce consistent results.
- The program sends one ping per second and waits up to 1 second for a reply before marking a packet as lost.
- IPv4 only — IPv6 is not supported.

---

## References

- Internet Control Message Protocol (RFC 792): https://datatracker.ietf.org/doc/html/rfc792
- Computing the Internet Checksum (RFC 1071): https://datatracker.ietf.org/doc/html/rfc1071
- Java `InetAddress` API Documentation: https://docs.oracle.com/javase/8/docs/api/java/net/InetAddress.html
- Java `DatagramSocket` API Documentation: https://docs.oracle.com/javase/8/docs/api/java/net/DatagramSocket.html
