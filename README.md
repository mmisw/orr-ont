[![Build Status](https://travis-ci.org/mmisw/orr-ont.svg?branch=master)](https://travis-ci.org/mmisw/orr-ont)
[![Coverage Status](https://coveralls.io/repos/github/mmisw/orr-ont/badge.svg?branch=master)](https://coveralls.io/github/mmisw/orr-ont?branch=master)



`orr-ont` is a new version of the `Ont` service.
See wiki.

**NOTE: these notes are still terse ..

## build `orr-ont` package

    sbt package
    
## build and push `mmisw/orr-ont` image

    docker build -t mmisw/orr-ont --no-cache .
    docker push mmisw/orr-ont
    

## deployment

### docker images:

    docker pull mongo
    docker pull franzinc/agraph
    docker pull mmisw/httpd
    docker pull mmisw/orr-ont
    
  
### preparations

    BASE_DIR=/home/carueda/orr-ont-base-directory
    MONGO_DATA=${BASE_DIR}/mongo-data
    
> bochica:
>	
>	BASE_DIR=/Users/carueda/orr-ont-base-directory
>	MONGO_DATA=${BASE_DIR}/mongo-dbpath
>	
    
    mkdir -p ${BASE_DIR}
    mkdir -p ${MONGO_DATA}
    
    scp somewhere:template.orront.conf ./orront.conf
    vim ./orront.conf   # edit if needed
    

    
### run containers

#### mongo

    docker run --name mongo -d \
           -p 27017:27017 \
           -v {MONGO_DATA}:/data/db \
           mongo
           
> Note (MacOS): Due to VirtualBox bug, -v not supported so no way to have 
> a local share for the mongo data.
>
>    ```
>    docker run --name mongo -d \
>           -p 27017:27017 \
>           mongo
>    ```
           
    
#### allegrograph

    docker run --name agraph -d \
           -e VIRTUAL_HOST=sparql.bochica.net \
           -e VIRTUAL_PORT=10035 \
           -m 1g -p 10000-10035:10000-10035 franzinc/agraph


    
#### orr-ont

    docker run --name orr-ont -d \
           --link mongo \
           --link agraph \
           --expose 8080 \
           -e VIRTUAL_HOST=bochica.net \
           -v `pwd`/orront.conf:/etc/orront.conf \
           -v ${BASE_DIR}:/opt/orr-ont-base-directory \
           -p 9090:8080 \
           mmisw/orr-ont

#### http proxy

    docker run --name httpd -d \
           -p 80:80 \
           --link mongo \
           --link agraph \
           --link orr-ont \
           mmisw/httpd
               
>
> nginx-proxy is very interesting ... but does not yet support paths,
> see eg., https://github.com/jwilder/nginx-proxy/pull/254
> 
> 	docker run --name nginx-proxy -d \
> 	           -p 80:80 \
> 	           -v /var/run/docker.sock:/tmp/docker.sock:ro \
> 	           jwilder/nginx-proxy
> 

### use

    open http://`docker-machine ip`/orr-ont
