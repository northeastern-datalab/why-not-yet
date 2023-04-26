# Why-not-yet Project

This repository provides an implementation of the framework for answering why-not-yet questions over top-k queries.
Also included are code and data to reproduce the experiments from our VLDB 2023 paper: _Why not yet: Fixing a top-k ranking that is not fair to individuals_.


## Programming Language and Dependencies

The source code of why-not-yet is written in Java, tested on JDK 18 and 11. 

This project uses [Maven](https://maven.apache.org/index.html) to manage libraries and compile the code. All library dependencies are specified in the `pom.xml` file.


## Compilation

To compile, navigate to the root directory of the project and run:
```
mvn package
```
Successful comilation will produce a jar file in `/target/` from which classes that implement a `main` function can be executed, e.g.
```
java -cp target/wny-1.0.jar wny.Query
```


## Required Libraries

The required external libraries, together with the version tested, are:
- Javatuples 1.2
- z3 4.8.17
- gurobi 9.5.2
- weka 3.8.6

Note that the maven repository does not provide Z3 and Gurobi, therefore the corresponding jar file needs to be obtained and provided locally.
Also, using Gurobi requires a license. (Free options are generally available, but this may change over time.)


## Code (src/main/java/wny)

`Experiment.java` runs all experiments for the VLDB 2023 paper.

The `data` folder is used for the synthetic data generation.

The `entities` folder contains all entity classes including `Relation`, `Tuple`, `Box` and `Constraint`.

The `query` folder provides the class for a why-not-yet query.

The `solver` folder contains the Gurobi and Z3 solvers.

The `util` folder is used for parsing a database.


## Data (data)

All data used in our experiments are here, including the NBA stats data and the synthetic data representing 3 different data distributions.


## Acknowledgement

Some code for the database parser and the tuple and relation data structure are originally from the [any-k repository](https://github.com/northeastern-datalab/anyk-code) by Nikos Tziavelis.
Thank you Nikos for sharing your code!

The NBA data were obtained on October 2, 2022, from the great baseketball stats and history website [Basketball Reference](https://www.basketball-reference.com/).
We appreciate and like the website a lot for its detailed stats.


## Contact

Zixuan Chen (chen.zixu@northeastern.edu)
