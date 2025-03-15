## Chapter 18: Load Balancing and Monitoring 🌍⚙️  

---
<details>
<summary>🚀 <strong>How LoadBalancerPro Works - Full System Breakdown</strong></summary>

LoadBalancerPro is a **scalable, cloud-integrated load balancing system** designed to efficiently distribute data across both **on-premise and cloud servers**. It employs **multiple load balancing strategies**, continuously **monitors server health**, and integrates with **AWS CloudWatch & Auto Scaling** to dynamically manage server capacity. The system provides both a **GUI and a CLI interface**, ensuring users can interact with it in their preferred way. Additionally, it includes a **logging and alert system** that tracks system activity, reports potential failures, and enables performance analysis through **CSV and JSON exports**.

### **Core System Components**  
At the heart of the system is **`LoadBalancer.java`**, which acts as the central controller for managing servers, distributing data, and executing different load-balancing algorithms. The load balancer supports **multiple balancing strategies**, including **Round Robin, Least Loaded, Weighted Distribution, Consistent Hashing, Capacity-Aware, and Predictive Balancing**, allowing it to adapt to different server configurations and workloads. It continuously interacts with **`Server.java`**, which represents individual servers and tracks their **CPU, Memory, and Disk usage**. Each server maintains a **health status**, which can be monitored and updated dynamically.

To ensure continuous monitoring, **`ServerMonitor.java`** runs as a background process, periodically checking the **status of all servers**. If a server becomes overloaded or fails, the monitor triggers **alerts** and logs the issue for further analysis. If cloud servers are in use, it also fetches **real-time CPU metrics from AWS CloudWatch**. These real-time updates help the load balancer make informed decisions when redistributing workloads.

Users interact with the system through **two primary interfaces**: **`LoadBalancerGUI.java`** and **`LoadBalancerCLI.java`**. The **GUI provides an intuitive interface** with buttons to **add servers, balance loads, check system status, and generate reports**, while the **CLI offers a command-driven approach** with full terminal-based control. Both interfaces allow users to **import server logs from CSV or JSON files**, making it easy to restore a previous state or analyze historical performance.

The system also includes **`Utils.java`**, which acts as a utility module for **handling file imports, log exports, and data hashing**. It ensures data consistency when using **Consistent Hashing** by implementing an **MD5-based hashing function**, and it provides the necessary functionality for **saving and loading server data** efficiently.

A key feature of LoadBalancerPro is its **cloud integration through `CloudManager.java`**, which allows it to interact with **AWS EC2, CloudWatch, and Auto Scaling**. The cloud manager enables the system to **dynamically scale cloud instances based on demand**, ensuring that additional servers are deployed when needed and decommissioned when traffic decreases. It can also **fetch real-time performance data from AWS CloudWatch** to provide accurate server utilization metrics.

</details>

<details>
<summary>☁️ <strong>Cloud Integration & AWS Auto Scaling</strong></summary>

### **AWS-Powered Load Balancing**  
LoadBalancerPro is built to **seamlessly integrate with AWS cloud infrastructure**, allowing it to **scale dynamically** based on demand. The **CloudManager module** interacts with **AWS EC2, CloudWatch, and Auto Scaling**, ensuring that cloud servers are automatically adjusted when system load increases or decreases.

### **How It Works**
1. **Instance Management:**  
   - The system **automatically deploys or terminates AWS EC2 instances** to match demand.  
   - Uses **Auto Scaling Groups (ASG)** to manage instance counts.  
   - Supports custom **Launch Templates** to define instance configurations.  

2. **CloudWatch Monitoring:**  
   - Tracks **real-time CPU, memory, and disk usage** for cloud servers.  
   - Adjusts **server loads dynamically** using CloudWatch insights.  

3. **Failover & Scaling:**  
   - Detects **overloaded servers** and **scales up new EC2 instances** as needed.  
   - **Terminates excess instances** when demand decreases to optimize cost.  
   - Can be configured for **predictive scaling** to preempt traffic spikes.  

### **Example Auto Scaling Workflow**
- If CPU utilization exceeds **85%**, the system **launches a new EC2 instance**.  
- If utilization falls below **30%**, it **terminates an idle instance**.  
- The load balancer **automatically reassigns traffic** to active cloud servers.  

### **Future Enhancements**
- **Lambda-Based Auto Healing** → Replace failed instances automatically.  
- **Multi-Cloud Support** → Expand beyond AWS to include **Azure & GCP**.  
- **AI-Driven Scaling** → Use machine learning to **predict resource needs** ahead of time.  

</details>


<details>
<summary>🖥️ <strong>Cloud API Commands & AWS Examples</strong></summary>

<h3>🚀 Auto Scaling EC2 Instances (CLI & API Examples)</h3>

<p>LoadBalancerPro can interact with AWS services <strong>programmatically</strong> or via <strong>command-line tools</strong>. Below are real AWS CLI commands and API request examples used for <strong>scaling EC2 instances dynamically</strong>.</p>

---

<h4>🔹 1. List All EC2 Instances</h4>
<pre><code>aws ec2 describe-instances --query "Reservations[*].Instances[*].[InstanceId,State.Name,InstanceType]"</code></pre>
<p>📌 <strong>Purpose:</strong> Fetches details about running instances.</p>

---

<h4>🔹 2. Launch a New EC2 Instance</h4>
<pre><code>aws ec2 run-instances --image-id ami-12345678 --count 1 --instance-type t3.micro --key-name MyKeyPair --security-groups MySecurityGroup</code></pre>
<p>📌 <strong>Purpose:</strong> Starts an EC2 instance with <strong>AMI ID `ami-12345678`</strong>.  
📌 <strong>Modify:</strong> Change <code>instance-type</code> and <code>security-groups</code> as needed.</p>

---

<h4>🔹 3. Terminate an EC2 Instance</h4>
<pre><code>aws ec2 terminate-instances --instance-ids i-0123456789abcdef0</code></pre>
<p>📌 <strong>Purpose:</strong> Shuts down an instance <strong>by instance ID</strong>.</p>

---

<h4>🔹 4. Check CPU Utilization via CloudWatch</h4>
<pre><code>aws cloudwatch get-metric-statistics --namespace AWS/EC2 --metric-name CPUUtilization --dimensions Name=InstanceId,Value=i-0123456789abcdef0 --statistics Average --period 60 --start-time $(date -u --date='5 minutes ago' +%Y-%m-%dT%H:%M:%SZ) --end-time $(date -u +%Y-%m-%dT%H:%M:%SZ)</code></pre>
<p>📌 <strong>Purpose:</strong> Retrieves <strong>average CPU utilization</strong> for an instance over the last <strong>5 minutes</strong>.</p>

---

<h4>🔹 5. Create an Auto Scaling Group</h4>
<pre><code>aws autoscaling create-auto-scaling-group --auto-scaling-group-name MyAutoScalingGroup --launch-template LaunchTemplateId=lt-0123456789abcdef0 --min-size 1 --max-size 5 --desired-capacity 2 --vpc-zone-identifier subnet-12345678</code></pre>
<p>📌 <strong>Purpose:</strong> Defines an <strong>Auto Scaling Group</strong> that can dynamically add/remove instances.  
📌 <strong>Modify:</strong> Adjust <code>min-size</code>, <code>max-size</code>, and <code>desired-capacity</code>.</p>

---

<h4>🔹 6. Adjust Desired Instance Count</h4>
<pre><code>aws autoscaling set-desired-capacity --auto-scaling-group-name MyAutoScalingGroup --desired-capacity 3</code></pre>
<p>📌 <strong>Purpose:</strong> Manually scales the <strong>number of running instances</strong>.</p>

---

<h4>🔹 7. Delete an Auto Scaling Group</h4>
<pre><code>aws autoscaling delete-auto-scaling-group --auto-scaling-group-name MyAutoScalingGroup --force-delete</code></pre>
<p>📌 <strong>Purpose:</strong> Shuts down and removes all instances managed by the <strong>Auto Scaling Group</strong>.</p>

---

<h3>🛠️ Future Enhancements</h3>

<ul>
  <li>🚀 <strong>Automated Lambda Scaling</strong> → Replace manual CLI commands with AWS Lambda event triggers.</li>
  <li>🌍 <strong>Multi-Cloud Deployment</strong> → Expand beyond AWS to support <strong>Azure & GCP scaling</strong>.</li>
  <li>📊 <strong>Predictive Scaling with AI</strong> → Use historical data for smarter instance adjustments.</li>
</ul>

</details>


<summary>📊 <strong>How the System Works Step-by-Step</strong></summary>

When the system starts, **either the GUI or CLI initializes the load balancer and launches the server monitor**. Users can **manually add servers** or **import a pre-configured list** from a log file. As data requests come in, the **load balancer distributes them based on the selected strategy**.  

Meanwhile, the **server monitor continuously checks all servers** for high CPU, memory, or disk usage. If a server exceeds safe operating limits, the monitor **logs an alert** and, if necessary, **triggers a failover** to reroute traffic. For cloud-based deployments, the **cloud manager automatically adjusts the number of AWS instances** based on real-time demand, ensuring smooth scalability.

At any point, users can **generate reports** through the GUI or CLI, exporting server statistics and alerts in **CSV or JSON format**. These reports provide insights into system performance, helping with troubleshooting and optimization. If the system needs to be shut down, the **cloud manager ensures all AWS instances are safely decommissioned** before exiting.

</details>

<details>
<summary>🔥 <strong>LoadBalancerPro's Behavoir</strong></summary>

LoadBalancerPro is designed to be a **highly adaptable, hybrid load balancing solution**. Unlike traditional load balancers that focus only on **physical infrastructure**, this system is capable of **handling both on-premise and cloud servers simultaneously**. The inclusion of **multiple load-balancing strategies** allows it to **adapt dynamically** to different workloads, optimizing resource utilization and preventing bottlenecks.  

Its **real-time monitoring system** ensures that potential failures are detected before they become critical, reducing downtime and improving system reliability and enabling better trend analysis for manual log taking if such a situation arises or if a particular environment requires this additional logging. The **AWS integration** takes this a step further by enabling **automatic scaling**, ensuring that resources are available when needed without manual intervention.  

By offering both **GUI and CLI interfaces**, LoadBalancerPro provides a **user-friendly experience** for less technical users while maintaining **full control through terminal commands** for power users. Its **logging and reporting features** also make it an excellent tool for performance analysis and debugging.

</details>

<details>
<summary>📈 <strong>Conclusion</strong></summary>

LoadBalancerPro combines **smart load distribution, continuous monitoring, and cloud automation** into a single, flexible system. Whether handling **on-premise servers, cloud infrastructure, or a mix of both**, it ensures **efficient resource allocation, failover management, and real-time insights**. With a **scalable architecture** and room for **future enhancements like predictive AI-based load balancing**, it stands as a **working solution for modern distributed computing needs**.

</details>



### **Implemented Methods**
<ul>
  <li>🔧 <code>balanceLoad()</code> – Distributes workload across servers using strategies like Round Robin, Least Loaded, or Weighted Distribution.</li>
  <li>🔧 <code>monitorServers()</code> – Continuously tracks CPU, memory, and disk usage, triggering alerts if metrics exceed 90%.</li>
  <li>🔧 <code>checkHealth()</code> – Detects unhealthy servers and removes them from the load balancing pool.</li>
  <li>🔧 <code>updateMetrics()</code> – Updates server metrics with random variations (±10%) every 5 seconds.</li>
</ul>

### **Supporting Methods**
<ul>
  <li>🔧 <code>startMonitoring()</code> – Initializes the monitoring thread for real-time server tracking.</li>
  <li>🔧 <code>stopMonitoring()</code> – Safely terminates the monitoring thread.</li>
  <li>🔧 <code>getAlertLog()</code> – Retrieves the list of triggered alerts.</li>
  <li>🔧 <code>addServer()</code> – Adds a new server to the load balancing pool.</li>
</ul>

---

<details>
<summary><strong>🌡️ monitorServers</strong></summary>

<p>
The <code>monitorServers</code> method in the <code>ServerMonitor</code> class operates within a dedicated thread, designed to oversee server health metrics every 5 seconds. It tracks CPU, memory, and disk usage for each server in the <code>LoadBalancer</code> pool, logging alerts whenever any metric surpasses the 90% threshold. For instance, if a server’s CPU usage reaches 95%, the method records an alert like "ALERT: Server S1 is HOT! CPU:95.00% Mem:50.00% Disk:50.00%" to the <code>alertLog</code> and outputs it via Log4j.
</p>

<p>
To simulate real-world conditions, <code>monitorServers</code> incorporates random metric updates (±10%) using the <code>updateMetrics</code> method. This variability ensures the system can handle fluctuating loads, making it usable for dynamic environments. The method runs in a continuous loop until explicitly stopped, with a <strong>O(N)</strong> time complexity where N is the number of servers, as it iterates over each one during each cycle.
</p>

<p>
<strong>Breakdown of method calls:</strong> The process follows a periodic check pattern, pausing for 5 seconds between iterations using <code>Thread.sleep</code>. If an alert condition is met, it synchronizes access to the <code>LoadBalancer</code> to safely log the alert. This ensures thread safety, though it may introduce minor delays under heavy contention.
</p>

</details>

---

<details>
<summary><strong>⚖️ balanceLoad</strong></summary>

<p>
The <code>balanceLoad</code> method in the <code>LoadBalancer</code> class is responsible for distributing a specified workload (e.g., 100GB) across all healthy servers in the pool. It supports a variety of strategies, including Round Robin (evenly splitting the load), Least Loaded (assigning to the server with the lowest current load), Weighted Distribution (based on server capacity), Consistent Hashing (for key-based distribution), Capacity-Aware (respecting server limits), and Predictive (anticipating future load based on trends).
</p>

<p>
For example, with a 100GB workload and two servers, Round Robin might allocate 50GB to each, while Least Loaded would prioritize the server with the lowest metric sum. The method first filters out unhealthy servers using <code>checkHealth</code>, then applies the chosen strategy. This flexibility makes it adaptable to different use cases, from simple clusters to complex distributed systems.
</p>

<p>
<strong>Breakdown of method calls:</strong> The process begins with a health check, followed by strategy-specific logic. For Round Robin, it cycles through servers; for Least Loaded, it sorts by metric totals. The time complexity is <strong>O(N)</strong>, where N is the number of servers, dominated by the iteration over the pool.
</p>

</details>

---

<details>
<summary><strong>🩺 checkHealth</strong></summary>

<p>
The <code>checkHealth</code> method, invoked within the <code>ServerMonitor</code> class, evaluates the health status of each server based on its current metrics. It considers a server unhealthy if its metrics drop below a minimum threshold (e.g., all zeros) or if it’s manually marked as unhealthy via <code>setHealthy(false)</code>. When a server is deemed unhealthy, it’s removed from the <code>LoadBalancer</code> pool to prevent workload assignment.
</p>

<p>
For instance, a server with metrics (20, 20, 20) remains active, but one with (0, 0, 0) is flagged and excluded. This method runs as part of the 5-second monitoring cycle, ensuring timely health updates. The removal process is synchronized to avoid race conditions with the balancing logic.
</p>

<p>
<strong>Breakdown of method calls:</strong> The method iterates over the server list, checks each <code>Server</code> object’s <code>isHealthy()</code> and metrics, and updates the pool accordingly. With a time complexity of <strong>O(N)</strong> where N is the server count, it’s efficient for real-time monitoring.
</p>

</details>

---

<details>
<summary><strong>📊 updateMetrics</strong></summary>

<p>
The <code>updateMetrics</code> method in the <code>Server</code> class modifies a server’s CPU, memory, and disk usage with random variations (±10%) every 5 seconds, triggered by the <code>ServerMonitor</code>. Starting with initial values like (50, 60, 70), it might adjust to (45-55, 54-66, 63-77), reflecting real-world fluctuations. This randomness simulates dynamic load changes, making the system more realistic.
</p>

<p>
The method ensures metrics stay within the 0-100% range, clamping values if necessary. It’s called synchronously within the monitoring loop, preserving thread safety. This constant adjustment helps test the load balancer’s adaptability to varying conditions.
</p>

<p>
<strong>Breakdown of method calls:</strong> The update occurs in a single pass per server, using a random number generator. The time complexity is <strong>O(1)</strong> per server, keeping the overhead minimal during monitoring cycles.
</p>

</details>

---

<details>
<summary><strong>⏩ startMonitoring</strong></summary>

<p>
The <code>startMonitoring</code> method in the <code>ServerMonitor</code> class initializes the monitoring thread, kicking off the continuous server health and metric tracking process. It creates a new <code>Thread</code> instance with the <code>ServerMonitor</code> as the <code>Runnable</code> and starts it, beginning the 5-second cycle of <code>monitorServers</code> and <code>checkHealth</code>.
</p>

<p>
This method is the entry point for real-time monitoring, ensuring the system remains responsive to server changes. It sets up the thread safely, avoiding duplicate starts by checking the thread’s state beforehand.
</p>

<p>
<strong>Breakdown of method calls:</strong> It involves a single <code>new Thread</code> creation and <code>start()</code> call, with <strong>O(1)</strong> time complexity, making it a lightweight initialization step.
</p>

</details>

---

<details>
<summary><strong>⏹️ stopMonitoring</strong></summary>

<p>
The <code>stopMonitoring</code> method in the <code>ServerMonitor</code> class safely terminates the monitoring thread. It sets a volatile <code>running</code> flag to <code>false</code>, allowing the thread to exit its loop gracefully. This method is called during test teardown or application shutdown to prevent resource leaks.
</p>

<p>
To ensure a clean stop, it also includes an optional <code>interrupt()</code> if the thread is stuck, followed by a <code>join(5000)</code> to wait up to 5 seconds; This prevents hangs in testing or production environments.
</p>

<p>
<strong>Breakdown of method calls:</strong> The process involves flag setting and thread state management, with <strong>O(1)</strong> time complexity, though the join may introduce a delay.
</p>

</details>

---

<details>
<summary><strong>📋 getAlertLog</strong></summary>

<p>
The <code>getAlertLog</code> method in the <code>LoadBalancer</code> class returns a list of all triggered alerts, stored as strings in the <code>alertLog</code> collection. Each alert details a server’s high metric event (e.g., "ALERT: Server S1 is HOT! CPU:95.00% Mem:50.00% Disk:50.00%"), logged by <code>monitorServers</code>.
</p>

<p>
This method provides a read-only view of the alert history, useful for debugging or monitoring. It’s synchronized to ensure thread safety when accessed concurrently with alert logging.
</p>

<p>
<strong>Breakdown of method calls:</strong> It simply returns the internal list, with <strong>O(1)</strong> time complexity for the access, though the list size may grow with server activity.
</p>

</details>

---

<details>
<summary><strong>➕ addServer</strong></summary>

<p>
The <code>addServer</code> method in the <code>LoadBalancer</code> class adds a new <code>Server</code> instance to the balancing pool. It accepts a server ID and initial metrics (CPU, memory, disk usage), creating a <code>Server</code> object and appending it to the internal server list.
</p>

<p>
This method is the primary way to expand the system, enabling dynamic scaling. It’s synchronized to prevent race conditions during concurrent operations like balancing or monitoring.
</p>

<p>
<strong>Breakdown of method calls:</strong> It involves object creation and list addition, with <strong>O(1)</strong> time complexity for the append operation.
</p>

</details>

---

<details>
<summary><strong>📖 Javadoc Documentation</strong></summary>
<details>
<summary><strong>🖼️ View Image</strong></summary>
 <br>
![image](https://github.com/user-attachments/assets/ce8bc1e2-e8cb-4876-a03f-ef8da6f4dc84)


 <br>
</details>
</details>

---

<details>
<summary><strong>📐 UML Class Diagram</strong></summary>
<details>
<summary><strong>🖼️ View Image</strong></summary>
 <br>
![cloudmanager](https://github.com/user-attachments/assets/10171999-c98c-49a7-890d-e5fa81bfeef8)

 ![loadbalancer](https://github.com/user-attachments/assets/5e921b3e-eaca-4eff-ba8b-e53efb93e3f7)
 ![CLI](https://github.com/user-attachments/assets/79a2e4dc-c7df-4552-9e3d-72585c80771b)
 ![GUI](https://github.com/user-attachments/assets/290d25f2-e29d-484d-a7de-21732d4891c8)
 ![server](https://github.com/user-attachments/assets/74f33278-6111-4360-b902-a0011f342b5b)
 ![servermonitor](https://github.com/user-attachments/assets/ae6eaf44-5a68-4e74-b1e9-58c09c531a15)
 ![image](https://github.com/user-attachments/assets/dfdd709d-68a8-4c1b-86b4-c61136cab5f8)




 



 <br>
</details>
</details>

---

<details>
<summary><strong>🧪 JUnit Testing</strong></summary>
<details>
<summary><strong>🖼️ View Image</strong></summary>
 <br>
![JUnit](https://github.com/user-attachments/assets/e42de9b6-58f7-41da-95c1-2d243f57823c)
![top](https://github.com/user-attachments/assets/4c2a36a6-1e1e-4a4b-836c-f3f208464ade)


 <br>
</details>
</details>

---

<details>
<summary><strong>📚 Works Cited: Load Balancing References</strong></summary>

### **📌 Core Programming References**

<ul>
  <li>🖥️ **Java `Thread` Class** → <a href="https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html">Java Thread Class</a>  
    <br> 🔹 Manages monitoring threads with <code>start()</code>, <code>interrupt()</code>, <code>join()</code>.
  </li>
  <li>⚙️ **JUnit 5 Core Annotations** → <a href="https://junit.org/junit5/docs/current/api/">JUnit 5 API</a>  
    <br> 🔹 Key annotations: <code>@Test</code>, <code>@BeforeEach</code>, <code>@AfterEach</code>.
  </li>
  <li>🛠️ **JUnit Assertions** → <a href="https://junit.org/junit5/docs/current/api/org/junit/jupiter/api/Assertions.html">JUnit Assertions</a>  
    <br> 🔹 Used methods: <code>assertEquals()</code>, <code>assertTrue()</code>, <code>assertFalse()</code>.
  </li>
  <li>🎨 **JavaFX Documentation** → <a href="https://openjfx.io/javadoc/17/">JavaFX API</a>  
    <br> 🔹 Used for GUI implementation.
  </li>
</ul>

---

### **📌 Load Balancing & Distributed Systems**

<ul>
  <li>🔗 **Load Balancing Theory**  
    <br> 📖 *Distributed Systems: Principles and Paradigms*, Elsevier, **3rd Edition, 2017*.
  </li>
  <li>📡 **Server Monitoring Concepts**  
    <br> 📌 <a href="https://www.geeksforgeeks.org/load-balancing-in-distributed-system/">GeeksforGeeks - Load Balancing</a>  
    <br> 🔹 Covers distribution strategies & health checks.
  </li>
  <li>📊 **Server Health Monitoring**  
    <br> 📌 <a href="https://www.geeksforgeeks.org/server-health-monitoring/">GeeksforGeeks - Server Monitoring</a>.
  </li>
  <li>⚡ **Distributed Computing & Networking**  
    <br> 📌 <a href="https://cs.stanford.edu/people/eroberts/courses/soco/projects/2000-01/load-balancing/intro.html">Stanford Load Balancing Research</a>  
    <br> 🔹 Covers network load balancing approaches.
  </li>
  <li>🖥️ **CompTIA Server+ Certification Guide**  
    <br> 📖 *CompTIA Server+ Study Guide*, Wiley, **2022 Edition**.  
    <br> 🔹 Covers server architecture, load balancing, and infrastructure security.
  </li>
</ul>

---

### **📌 Open-Access Research Papers & Online Articles**

<ul>
  <li>📘 **Proposing a Load Balancing Algorithm for Cloud Computing Applications**  
    <br> 🔹 Introduces a resource-based task allocation algorithm aimed at optimizing energy consumption in multi-cloud networks.  
    <br> 🔗 <a href="https://www.researchgate.net/publication/353792411_Proposing_a_Load_Balancing_Algorithm_For_Cloud_Computing_Applications">ResearchGate</a>
  </li>
  <li>📊 **Performance Evaluation of Load-Balancing Algorithms with Different Cloud Computing Workloads**  
    <br> 🔹 Compares Round Robin, Particle Swarm Optimization (PSO), Equally Spread Current Execution (ESCE), and Throttled Load Balancing strategies.  
    <br> 🔗 <a href="https://www.mdpi.com/2076-3417/13/3/1586">MDPI Research Paper</a>
  </li>
  <li>🌍 **CoDel: Controlled Delay Active Queue Management**  
    <br> 🔹 Analyzes CoDel’s role in preventing bufferbloat through active queue management in networking systems.  
    <br> 🔗 <a href="https://en.wikipedia.org/wiki/CoDel">Wikipedia - CoDel</a>
  </li>
</ul>

---

### **📌 AWS & Cloud References**

<ul>
  <li>☁️ **AWS EC2 Documentation** → <a href="https://docs.aws.amazon.com/ec2/index.html">AWS EC2</a>  
    <br> 🔹 Used for managing cloud servers.
  </li>
  <li>📈 **AWS CloudWatch Metrics** → <a href="https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/WhatIsCloudWatch.html">AWS CloudWatch</a>  
    <br> 🔹 Used for real-time CPU tracking.
  </li>
  <li>⚡ **AWS Auto Scaling Docs** → <a href="https://docs.aws.amazon.com/autoscaling/index.html">AWS Auto Scaling</a>  
    <br> 🔹 Used for dynamic scaling.
  </li>
  <li>🌐 **Cloud Computing Load Balancing**  
    <br> 📌 <a href="https://cloud.google.com/load-balancing/docs">Google Cloud Load Balancing Docs</a>  
    <br> 🔹 Covers modern cloud-based balancing strategies.
  </li>
</ul>

---

### **📌 Code-Intensive References (NEW ADDITIONS!)**  

<ul>
  <li>💻 **Code Example: Implementing a Custom Load Balancer in Java**  
    <br> 📌 <a href="https://www.baeldung.com/java-load-balancing">Baeldung - Load Balancing in Java</a>  
    <br> 🔹 Covers Round Robin, Least Loaded, and Weighted Load Balancing with **Java code**.
    <br> 🔹 Includes **full source code & test cases** for **server selection algorithms**.
  </li>
  <li>⚙️ **Code Example: Real-Time Server Health Monitoring in Java**  
    <br> 📌 <a href="https://www.baeldung.com/java-server-monitoring">Baeldung - Java Server Monitoring</a>  
    <br> 🔹 Demonstrates how to track **CPU, memory, and disk usage** with Java.
    <br> 🔹 Provides **multi-threaded server health checks** + **live logging**.
  </li>
</ul>

---

### **📌 Tools & Utilities**

<ul>
  <li>📐 **PlantUML** → <a href="https://plantuml.com/class-diagram">PlantUML Class Diagram</a>  
    <br> 🔹 Used for generating UML diagrams.
  </li>
  <li>📝 **Javadoc Standards** → <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/javadoc/doc-comment-spec.html">Oracle Javadoc</a>  
    <br> 🔹 Concepts used: <code>@param</code>, <code>@return</code>.
  </li>
  <li>📄 **GitHub Markdown** → <a href="https://docs.github.com/en/get-started/writing-on-github">GitHub Markdown</a>  
    <br> 🔹 Used for README formatting.
  </li>
</ul>

---

---

### **📌 Open-Source Load Balancers & Tools**  

<ul>
  <li>🔀 **HAProxy** → <a href="https://www.haproxy.org/">HAProxy</a>  
    <br> 🔹 A free, high-performance TCP/HTTP load balancer and proxy server.
  </li>
  <li>🌐 **Traefik** → <a href="https://doc.traefik.io/traefik/">Traefik Documentation</a>  
    <br> 🔹 An open-source edge router & load balancer for modern microservices.
  </li>
  <li>⚡ **Nginx Load Balancing** → <a href="https://nginx.org/en/docs/http/load_balancing.html">Nginx Documentation</a>  
    <br> 🔹 A web server that also functions as a reverse proxy & load balancer.
  </li>
  <li>🛡️ **Pound** → <a href="https://en.wikipedia.org/wiki/Pound_(networking)">Wikipedia - Pound Load Balancer</a>  
    <br> 🔹 A lightweight reverse proxy & application firewall for web load balancing.
  </li>
</ul>

---

### **📌 Additional References**

<ul>
  <li>📚 **Andrew S. Tanenbaum & Maarten Van Steen**  
    <br> *Distributed Systems: Principles and Paradigms*, Elsevier, **3rd Edition, 2017**.
  </li>
  <li>📚 **Martin Kleppmann**  
    <br> *Designing Data-Intensive Applications*, O’Reilly, **2017**.
  </li>
  <li>📚 **CompTIA Server+ Certification Guide**  
    <br> *CompTIA Server+ Study Guide*, Wiley, **2022 Edition**.
  </li>
</ul>

</details>

---

<details>
<summary><strong>🗄️ LoadBalancerPro System Description</strong></summary>

The <code>LoadBalancerPro</code> system integrates <code>LoadBalancer</code>, <code>Server</code>, and <code>ServerMonitor</code> classes to manage server workloads and health.

### **Core Components**
- **`LoadBalancer`**: Handles server pool and workload distribution.
- **`Server`**: Stores metrics and health status.
- **`ServerMonitor`**: Monitors and updates servers in a thread.

### **Key Methods**
- **`balanceLoad()`** → Distributes workload.
- **`monitorServers()`** → Tracks and alerts.
- **`checkHealth()`** → Manages health.
- **`updateMetrics()`** → Adjusts metrics.

### **Internal Structure**
- **Thread-Based Monitoring**: Runs every 5 seconds.
- **Dynamic Updates**: ±10% metric changes.
- **Performance**: 
  - **Balancing** → `O(N)`
  - **Monitoring** → `O(N)`

<p>
The system uses a thread to monitor servers, updating metrics randomly to mimic real-world loads. Load balancing strategies optimize resource use, while alerts notify of high usage (>90%). The design supports scalability, with each component handling its role efficiently.
</p>

<p>
Health checks ensure only operational servers receive workloads, enhancing reliability. The GUI and CLI interfaces provide flexible control, making it suitable for various deployment sizes.
</p>

<p>
The integration of Log4j logging allows for detailed tracking, stored in `debug_log.txt`, aiding in debugging and performance analysis.
</p>

</details>

---

<details>
<summary><strong>📝 Tester Description</strong></summary>

The JUnit tester for <code>LoadBalancerPro</code> validates core functionality.

### **JUnit Test Cases**
✅ `testMetricUpdatesOccur()` → Checks metric changes over time.  
✅ `testAlertTriggeredOnHighCPU()` → Verifies alert triggering at high CPU.  
✅ `testNoAlertBelowThreshold()` → Confirms no alerts for low metrics.  
✅ `testMultipleServerMonitoring()` → Tests multi-server alert behavior.  
✅ `testNoMetricUpdatesForUnhealthyServer()` → Ensures unhealthy servers stay static.  
✅ `testGracefulShutdown()` → Validates thread shutdown reliability.  

</details>

---
🐯
---
