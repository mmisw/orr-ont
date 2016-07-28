[![Stories in Ready](https://badge.waffle.io/mmisw/orr-ont.png?label=ready&title=Ready)](https://waffle.io/mmisw/orr-ont)
[![Build Status](https://travis-ci.org/mmisw/orr-ont.svg?branch=master)](https://travis-ci.org/mmisw/orr-ont)
[![Coverage Status](https://coveralls.io/repos/github/mmisw/orr-ont/badge.svg?branch=master)](https://coveralls.io/github/mmisw/orr-ont?branch=master)


# What's orr-ont?

`orr-ont` is a complete new implementation of the 
[`Ont` service](https://github.com/mmisw/mmiorr/tree/master/org.mmisw.ont) prototype.
See [wiki](https://github.com/mmisw/orr-ont/wiki).


# Deployment

See [DEPLOYMENT.md](https://github.com/mmisw/orr-ont/blob/master/DEPLOYMENT.md).

# `orr-ont` build 

By default, the `orr-ont` build will only have the repository backend services.
The following are steps to build it also including the 
[orr-portal](https://github.com/mmisw/orr-portal) UI.
 
- Clone this `orr-ont` repository;
- Clone the [orr-portal](https://github.com/mmisw/orr-portal) repository;
- See [`orr-portal`'s README](https://github.com/mmisw/orr-portal/blob/master/README.md) 
  for instructions to build and install the UI under `orr-ont` so everything gets included;
- Then, under your `orr-ont` clone:
		
        $ sbt test package
        $ bin/dockerize.sh 3.0.4-alpha
		$ docker push mmisw/orr-ont:3.0.4-alpha
