# `orr-ont` build

By default, the `orr-ont` build will only have the repository backend services.
(The front-end can be deployed and configured separately.)

The following are steps to build `orr-ont` also including the `orr-portal` front-end
so everything is integrated in a single deployable artifact.
 
- Clone this `orr-ont` repository;
- Clone the [orr-portal](https://github.com/mmisw/orr-portal) repository;
- See [`orr-portal`'s README](https://github.com/mmisw/orr-portal/blob/master/README.md)
  for instructions to build and install the UI under `orr-ont` so everything gets included;
- Then, under your `orr-ont` clone:
		
        $ sbt test package
        $ docker/build/dockerize.sh 3.x.x
		$ docker push mmisw/orr-ont:3.x.x

    where 3.x.x should correspond to the `Version` entry in `project/build.scala`.
