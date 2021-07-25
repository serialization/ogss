# Object Graph Serialization System
This is the OGSS code generator.

License: Free as in free markets

Prerequisits:
```
Have Java 8 or 11 installed
Download ogss.jar from this repository
```

Help:
```
java -jar ogss.jar -h
```

Usage:
```
java -jar ../ogss.jar build spec.sidl -L doxygen -p MyProject -o doc
```

TODO Link getting started: Hello World with SIDL and Java

## About

The Object Graph Serialization System serves the purpose of providing a type-safe and change-tolerant means of serializing graphs of objects in a scalable and performant way.
Existing OGSS-based tools can be reused for extended specifications without change or need for recompilation.

The specification language is object-oriented.
Objects can store pointers to other objects in the same graph without further restrictions.
Objects are accessed via a generated API provided by this tool.
The generated API provides means of iterating over all objects loaded from a file.
The generated API provides means of iterating over all type and field information from a loaded file – even if not contained in the original specification.
The generated API exposes comments from the specification.

Objects are serialized to a binary file format.
This format contains an efficient representation of the type system to realize both type safety and change tolerance.
Objects are decomposed into their field data and stored efficiently in a chunked column store.
This allows fast and parallel reading and writing of graphs leading to unmatched scalability.

## Front-Ends

Front-Ends serve as specification or schema definitions.
They instruct the code generator how your generated code should look like.
Also, they define what files can be read and written by the generated code.

File Extension|Name|Description
--------------|----|------------
  *.oil     |OIL      |OGSS Intermediate Language. This is the internal OGSS-based representation of any specification.
  *.skill   |skill    |SKilL Specification: TODO Link getting started
  *.sidl    |SIDL     |SIDL Specification: TODO Link getting started

Note: TODO Link differences between OGSS and SKilL semantics

## Back-Ends

Back-Ends determine the output format of a generator invocation call.
Programming language back-ends will generate code that will enable you to easily work with serialized graphs matching the specification that you provided.
Specification back-ends can be used to convert specifications between different formats. Also, they can be used to project interfaces to classes.

Name|Language|Supported Versions|Description
----|--------|------------------|------------
Cpp | C++    | 11 and newer     | TODO Link getting started
doxygen|C++ headers| any        | doxygen-compatible c++ headers used to create an html docu for your specification
Java| Java   | 11; 8 should work| TODO Link getting started
OIL | OIL    | latest only      | OGSS Intermediate Language
SKilL| SKilL | any              | Emit the input specification in a single file for each projection variant.
Scala| Scala | 2.12             | TODO Link getting started
SIDL| SIDL   | any              | Emit the input specification in a single file for each projection variant.


## Viewing Serialized Graphs

A general purpose binary graph viewer exists in the form of [OGSS View 2](https://github.com/serialization/ogssView2).
This is most useful to check if and what is in a file.
Also, it can be used to check if a file can be read at all.
Unreadable files can be caused by memory corruption in C++ tools.

A good approach in practice is to use a generated API for your specification language to create a custom viewer using DOT.
Usually, this requires only around 100 lines of code and allows domain-specific reduction of presented information.

## Known Active Users

These projects are reportedly using OGSS:

### Tyr Compiler

[Project Page](https://github.com/tyr-lang/releases)

ASTs are used in a make-and-forget style. Uses Java, doxygen and SIDL.

Compiled packages are used in a make-and-work-on style. Uses Java, C++, doxygen and SIDL.


## Documentation, Bugs, PRs, Feature Requests

Feel free to contact me or create a ticket if you notice a bug or if documentation lacks clarity.
If you miss a feature or support for another programming language reach out, too.
This project is maintained as a hobby.


## Scientific Background

OGSS is basically [SKilL](https://github.com/skill-lang/skill) with changes made based our learnings during or after preparation of my PhD Thesis on the [subject](http://dx.doi.org/10.18419/opus-9661).
OGSS uses the same concept of change tolance.
Also, the type and object representation algorithms have been reused.
However, the binary file format is not compatible.
This is caused by improvements leading to better performance and scalability of serialization.
Lastly, the concept of type order has been extended to enable pointers to containers.
