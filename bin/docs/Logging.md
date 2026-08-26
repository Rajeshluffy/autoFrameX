2.6 Logging Framework

Primary: SLF4J + Logback



autoFrameX uses Java util logging. Upgrade to:



<!-- pom.xml -->

<dependency>

&#x20;   <groupId>org.slf4j</groupId>

&#x20;   <artifactId>slf4j-api</artifactId>

&#x20;   <version>2.0.5</version>

</dependency>

<dependency>

&#x20;   <groupId>ch.qos.logback</groupId>

&#x20;   <artifactId>logback-classic</artifactId>

&#x20;   <version>1.4.7</version>

</dependency>

Logback config (src/main/resources/logback.xml):



<?xml version="1.0" encoding="UTF-8"?>

<configuration>



&#x20; <!-- Console output with color -->

&#x20; <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">

&#x20;   <encoder>

&#x20;     <pattern>%d{HH:mm:ss.SSS} \[%thread] %-5level %logger{36} - %msg%n</pattern>

&#x20;   </encoder>

&#x20; </appender>



&#x20; <!-- File output (JSON format for ELK Stack ingestion) -->

&#x20; <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">

&#x20;   <file>logs/test-execution.log</file>

&#x20;   <encoder class="net.logstash.logback.encoder.LogstashEncoder">

&#x20;     <customFields>{"env":"${env}","testSuite":"${testSuite}"}</customFields>

&#x20;   </encoder>

&#x20;   <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">

&#x20;     <fileNamePattern>logs/archive/test-%d{yyyy-MM-dd}-%i.log.gz</fileNamePattern>

&#x20;     <maxFileSize>100MB</maxFileSize>

&#x20;     <maxHistory>30</maxHistory>

&#x20;   </rollingPolicy>

&#x20; </appender>



&#x20; <!-- Async logging (non-blocking, high throughput) -->

&#x20; <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">

&#x20;   <queueSize>1024</queueSize>

&#x20;   <appender-ref ref="FILE"/>

&#x20; </appender>



&#x20; <!-- Root logger -->

&#x20; <root level="INFO">

&#x20;   <appender-ref ref="CONSOLE"/>

&#x20;   <appender-ref ref="ASYNC"/>

&#x20; </root>



&#x20; <!-- Framework loggers -->

&#x20; <logger name="com.framework" level="DEBUG"/>

&#x20; <logger name="org.openqa.selenium" level="WARN"/>

&#x20; <logger name="io.restassured" level="DEBUG"/>



</configuration>

Integration with ELK Stack:



Logback (JSON logs) 

&#x20; ↓ (Filebeat shipper)

Elasticsearch (indexed, searchable)

&#x20; ↓ (Kibana visualization)

Dashboard: Visualize test failures by category, time, component

