# Whisk (yet another build tool)

## Status
Current CI uses gradle to build `whisk`. The resulting application is then used to build itself: 

![Build Status](https://api.travis-ci.org/Bytekeeper/whisk.svg?branch=master)

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

