## DataTable

**DataTable** is a Java library for reading tabular information into memory,
summarizing the information using descriptive statistics, and selecting subsets
of the data using range queries.  The library was originally designed to support the 
[EDEN](http://github.com/csteed/eden) visualization tool.  The lead developer is
[Chad A. Steed](http://csteed.com).

If you are using **DataTable** for your work, we would greatly appreciate you citing the following paper:

Chad A. Steed, Daniel M. Ricciuto, Galen Shipman, Brian Smith, Peter E. Thornton, Dali Wang, and Dean N. Williams. Big Data Visual Analytics for Earth System Simulation Analysis. Computers & Geosciences, 61:71–82, 2013. doi:10.1016/j.cageo.2013.07.025  http://dx.doi.org/10.1016/j.cageo.2013.07.025

### Compiling the Source Code

Compiling **DataTable** is straightforward.  The first step is to clone the repository.  We supply a [Maven](http://maven.apache.org/) POM file to deal with the dependencies.  In the Eclipse development environment, import the code as a Maven project and Eclipse will automatically build the class files.  

To compile **DataTable** on the command line, issue the following commands:

```
$ mvn compile
$ mvn package
```

### Documentation

DataTable classes are fairly simple to follow by reading the source code.  Unfortunately,
we do not yet have extensive documentation on the classes.  In the future, we hope to 
provide, at a minimum, javadocs of each class and sample code for use.  The 
[EDEN](http://github.com/csteed/eden) tool uses DataTable so one could use the EDEN source 
code as an extensive example.
