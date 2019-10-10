# Whisk (yet another build tool)
Using `phases` or `task` dependency graphs is good, but limited. The build system doesn't really understand what
you are trying to build. And without proper documentation, developers don't understand why a certain task depends
on another.

Whisk tries to solve this by making dependencies explicit. Build steps depend on the output of other build steps instead 
of a generic execution of another task. This immediately makes clear if build steps could be removed, or why they are
there.

The build language to describe a build is a simple functional language based on goals and rules.

Goals are parameter-less targets a build can produce. They define the rules to execute. They can be called by
the CLI or by rules.

Rules are parametrized descriptions on what step to perform. They can not be called from the CLI. They can be called
by other rules or goals. Think of rules as a mean to the end of executing a goal. 

## Status
Current CI uses gradle to build `whisk`. The resulting application is then used to build itself: 

![Build Status](https://api.travis-ci.org/Bytekeeper/whisk.svg?branch=master)

## Examples
The `examples` folder has some simple examples for builds.

## Why
Besides being an interesting topic:
* Maven
    * Powerful and extendable
    * Verbose 
    * Not all builds neatly fit into the lifecycle phases
    * Calling a plugin multiple times in build can be difficult
    * Parallel builds depend on plugin supporting it
    * Incremental builds are "kind of" supported
* Gradle
    * Powerful and extendable and scriptable
    * Lean build configs
    * Breaks compatibility often
    * Very fast
    * Strange syntax sometimes
    * Sometimes cryptic build files due to abusing groovy/kotlin
    * Task based dependencies, but less knowledge about "who built what exactly?"
    * Dependencies are not inferred, the dev has to actively "know" which task depend on one another
* Bazel
    * Powerful and extendable and scriptable
    * Reduced python better than full programming language
    * Strange folder linking
    * Windows port "works"
    * Good dependency graph
    * Using maven dependencies required extra plugins
* Buck
    * See Bazel
    * Can't use maven dependencies
    

## Design Goals
* Based on dependencies and not tasks 
* Reproducible builds
* A DSL build language as simple as possible
* Control over build process
    * No magic build steps
    * Magic build steps if necessary
    * Powerful like ant/make
    * But with flexible order of execution
    * Execution order like maven
    * But not with "fixed" phases or static modules
    * Execution order like gradle
    * But not by declaring task dependencies "indirectly"

# Guide

## Build Lang
```text
# Load exposed goals/functions from the package whisk and core.test
import whisk,core.test

# Expose doit and bla for other BL scripts
export doit, glob

# Define blub as external rule/goal/function. It can only be used as 'name = blub(...)'. 
blub(a,b:[],c?)

# Using the 'anon' keyword, define glob as callable without name, it can also be used as 'x(glob(y))'
anon glob([])  

# Define bla as helper function
bla(a,b) = blub(a = a, b = [b]) 

# Declare doit as callable goal, ie. "whisk doit" 
doit = bla('test', [])
```

## Rules

### Anon Rules
By default, rules cannot be called from within other rules. This prevents complexity in build scripts and also allows 
easier management of generated file by Whisk. 
