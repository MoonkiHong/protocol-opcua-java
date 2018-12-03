OPC-UA Protocol Stack
================================

## Prerequisites ##

- JDK
  - Version : 1.8
  - [How to install](https://docs.oracle.com/javase/8/docs/technotes/guides/install/linux_jdk.html)
  - Eclipse Setting
     - Select Project -> Preference - Java Build Path - JRE System Library -Edit -> 
            Installed JREs - Add - Standard VM - Next -input Path installed JDK 1.8 -> finish 
- Maven
  - Version : 3.5.2
  - [Where to download](https://maven.apache.org/download.cgi)
  - [How to install](https://maven.apache.org/install.html)
  - [Setting up proxy for maven](https://maven.apache.org/guides/mini/guide-proxies.html)

## How to build  ##

#### 1. Command ####

```shell
$ ./build.sh
```
 ![build_stack](../documents/readme_images/build_stack.png)

##### Binaries #####
- opcua protocol stack library : edge-opcua/targets/opcua-adapter-0.0.1-SNAPSHOT-jar-with-dependencies.jar

#### 2. Eclipse ####

1. import opcua stack project(*protocol-opcua-java/edge-opcua*)
 ![build_2_1](../documents/readme_images/build_2_1.png)

2. Select Project -> Click Right button

3. Run As -> Run Configurations

4. insert goal : `assembly:single install -U`
   ![build_2_2](../documents/readme_images/build_2_2.png)

5. Run As -> `Maven Build`

##### Binaries #####
- opcua protocol stack library : targets/opcua-adapter-0.0.1-SNAPSHOT-jar-with-dependencies.jar

## How to run ##

#### Test ####
  - [How to test with sample client/server](../example/README.md)
