# PulseLang

Compile & Run:

```
javac -d out ./src/pulse/*.java
``` 
```
java -classpath out pulse.Pulse [sourcefile]
```
- (Can also just open as an IntelliJ IDEA project)

Usage:
 1. Pass a file as an argument and evaluates each statement from it, or
 2. No arguments: run a REPL which evaluates each statement entered.
     - Enter `exit` to exit program. 

Notes:
 - Source -> Scanner -> Parser -> Interpreter
 - Statements and Expressions are built into a syntax tree by the parser and evaluated by post-order traversal
 - Evaluation of expressions and statements are performed using the Visitor Design Pattern
