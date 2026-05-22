@echo off
echo [DLQ Analyzer] Starting with Asia/Kolkata timezone...
set JAVA_OPTS=-Duser.timezone=Asia/Kolkata
mvnw spring-boot:run
pause