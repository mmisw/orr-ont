#!/bin/bash

function usage() {
	echo "Usage:"
	echo "  docker/build/dockerize <version>"
	echo
	echo "Example:"
	echo "  docker/build/dockerize 3.1.1"
	echo
}

version=$1

if [ "$version" != "" ]; then
	cat docker/build/Dockerfile.in | sed "s/@@version/$version/g" > ./Dockerfile
	docker build -t "mmisw/orr-ont:$version" --no-cache .
else
	usage
fi
