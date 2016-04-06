[![Build Status](https://travis-ci.org/mmisw/orr-ont.svg?branch=master)](https://travis-ci.org/mmisw/orr-ont)
[![Coverage Status](https://coveralls.io/repos/github/mmisw/orr-ont/badge.svg?branch=master)](https://coveralls.io/github/mmisw/orr-ont?branch=master)



`orr-ont` is a preliminary prototype for a new version of the `Ont` service.
See wiki.


## build package

	sbt package
	
## build and push image

	docker build -t carueda/orr-ont --no-cache .
	docker push carueda/orr-ont
	
## deploy on target machine

Determine host base directory to store files (mongo data and ontology files), 
e.g.,

	mkdir -p /home/carueda/orr-ont-base-directory
	
### run mongo

	docker pull mvertes/alpine-mongo

	mkdir -p /home/carueda/orr-ont-base-directory/mongo-data
	
	docker run -d --name mongo \
	       -p 27017:27017 \
           -v /home/carueda/orr-ont-base-directory/mongo-data:/data/db \
      	   mvertes/alpine-mongo
	
### configure orr-ont

	scp somewhere:template.orront.conf /home/carueda/orront.conf
	vim /home/carueda/orront.conf   # edit if needed
	
### deploy and run orr-ont

	docker pull carueda/orr-ont
	
	docker run -d --name orr-ont \
		   --link mongo \
	       -v /home/carueda/orront.conf:/etc/orront.conf \
	       -v /home/carueda/orr-ont-base-directory:/opt/orr-ont-base-directory \
	       -p 9090:8080 orr-ont

