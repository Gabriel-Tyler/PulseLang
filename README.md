# PulseLang

Pulse Programming Language based on the book Crafting Interpreters: 
 - https://craftinginterpreters.com/contents.html 

Use:
 1. Pass a file as an argument and evaluate a single expression from it, or
 2. No arguments: run a REPL which evaluates each expression entered.

Notes:
 - Prefer using the REPL (no arguments)
 - Source -> Scanner -> Parser -> Interpreter
 - Expressions are built into a syntax tree by the parser and evaluated by post-order traversal
 - Evaluation of expressions are performed using the Visitor Design Pattern
