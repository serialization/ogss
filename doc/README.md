This is a short introduction to OGSS using the SKilL specification language and the Java programming language.
Other introductory material can be found in this folder.

## First Steps

First, we create a file *hello.skill* and insert these lines:
```
/* My first type */
Hello {
  /* My first field */
  string world;
}
```

Now, we can create a bindig.
A binding is a generated API plus generated implementation plus libraries for a given language and specification.
To get a Java binding, we have to invoke the ogss.jar:
```
java -jar ogss.jar build hello.skill -L java -p generated.code
```

This will create a folder *generated* and two files *ogss.common.java.jar* and *ogss.common.jvm.jar* in your current directory.

The jars are required by the generated code.
Their placement can be controled by the **-d** parameter.

The folder contains the generated code.
Its placement can be controlled by the **-o** parameter.

Let's have a look at it.
In the folder *generated/code* are four files: *Hello.java*, *Sub$Hello.java*, *OGFile.java* and *internal.java*.
The *Sub$*** and *internal* files are required by the code generator and should not bother us.
So, let's open *Hello.java*.

It contains the class Hello, which serves as in-memory representation of our Hello type.
Note that the package is the one provided by the **-p** parameter.
Also, the field *world* is made accessible via a getter/setter pair and the value is stored in a field.


### Working with Graphs

Now, let's turn to *OGFile.java*.
Graphs are managed by the generated OGFile class.
An OGFile can be created by calling one of the static *open* methods.
They will always take a path and up to two Modes.
The Modes **Create** and **Read** control if the graph shall be created by actually reading a file.
The Modes **Write** and **ReadOnly** control if the graph shall be written on flush or close.
The defaults are **Read** and **Write**.
The path argument is created to set the current path of the graph.
Both, write mode and current path can be changed later if required.

Note that there is a field *Hellos* in OGFile.
For each specified type, such a field will exist.
Its purpose is to make *Hello* instances of the graph accessible.
It can be used to create a new instance using its *build* method:
```
try (OGFile graph = OGFile.open("out.sg", Mode.Create, Mode.Write)) {
  graph.Hellos.build().world("Hello World!").make();
}
```
This will create a file *out.sg* containing a single Hello instance whose world field will point to the string "Hello World!".

### Working with Objects

The various *make* methods return a pointer to the newly created instance.
```
try (OGFile graph = OGFile.open("out.sg", Mode.Create, Mode.Write)) {
  var h = graph.Hellos.build().world("Hello World!").make();
  h.setWorld("new world");
}
```

This will cause world to point to the string "new world" instead.
"Hello World!" will not be contained in *out.sg* as it is not used when *graph* is closed.

Now, we can read the file again to get our data:
```
try (OGFile graph = OGFile.open("out.sg", Mode.Read, Mode.ReadOnly)) {
  for(var h : graph.Hellos)
    System.out.println(h.getWorld());
}
```
This will print *world* of every *Hello* found in the file *out.sg*.
So, likely, you will get a single line "new world".
