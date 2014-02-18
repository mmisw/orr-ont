# ORR Ont #

## Build & Run ##

```shell
$ cd orr-ont
$ ./sbt
> test
> container:start
```


```shell
$ http get localhost:8080/ont/

$ http post localhost:8080/ont/ uri=uri1 name=name1

$ http put localhost:8080/ont/ uri=uri1 name=name1-modified

$ http post localhost:8080/ont/\!/deleteAll  pw=XXX
```
