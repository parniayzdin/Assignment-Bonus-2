[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=parniayzdin_Assignment-Bonus-2&metric=alert_status)](https://sonarcloud.io/summary?id=parniayzdin_Assignment-Bonus-2)

# Assignment-Bonus-2
This repository contains a simple proof-of-concept model-to-code generator. It reads a draw.io UML-style XML model and generates Java class files representing the classes, inheritance, and associations shown in the diagram.

## How to get started:
Pull everything from the repo!

```bash
cd examples/default/src-gen/src
javac ModelToJava.java
```
Generate the Java files
```
java ModelToJava "..\..\model\carModel.drawio" "..\..\src-gen-output"
```
