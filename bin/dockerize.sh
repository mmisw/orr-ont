#!/bin/bash

function usage() {
	echo "Usage:"
	echo "  bin/dockerize <version>"
	echo
	echo "Example:"
	echo "     bin/dockerize 3.0.2-alpha"
	echo
}

version=$1

if [ "$version" != "" ]; then
	cat Dockerfile.in | sed "s/@@version/$version/g" > ./Dockerfile
	docker build -f ./Dockerfile -t mmisw/orr-ont --no-cache .
else
	usage
fi
