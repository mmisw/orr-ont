#!/bin/bash

function usage() {
	echo "Usage:"
	echo "  bin/dockerize <version>"
	echo
	echo "Example:"
	echo "     bin/dockerize 3.0.0-beta"
	echo
}

version=$1

if [ "$version" != "" ]; then
	cat Dockerfile.in | sed "s/@@version/$version/g" > ./Dockerfile
	docker build -t "mmisw/orr-ont:$version" --no-cache .
else
	usage
fi
