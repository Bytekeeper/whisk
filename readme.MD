# Whisk (yet another build tool)

## Design Goals
* Based on dependencies and not tasks 
* Reproducible builds
* A DSL build language as simple as possible

## Build Lang
```text
# Load exposed goals/functions from the package whisk and core.test
import whisk,core.test

# Expose doit and bla for other BL scripts
export doit, glob

# Define blub as external rule/goal/function. It can only be used as 'name = blub(...)'. 
blub(a,b:[],c?)

# Define glob as callable without name, it can also be used as 'x(glob(y))'
glob([])  

# Define bla as helper function
bla(a,b) = blub(a = a, b = [b]) 

# Declare doit as callable goal, ie. "whisk doit" 
doit = bla('test', [])
```

