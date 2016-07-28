# Deployment

Only basic requirement is to have a [Docker engine](https://www.docker.com/products/docker-engine)
on the target system. 
The user performing the deployment should have the relevant Docker privileges.
Check with your sysadmin.

The docker images comprising the ORR system are:

- [`mongo`]          (https://hub.docker.com/_/mongo/)
- [`franzinc/agraph`](https://hub.docker.com/r/franzinc/agraph/)
- [`mmisw/orr-ont`]  (https://hub.docker.com/r/mmisw/orr-ont/)


### Preparations

- Designate a directory on your host machine as a base location for all data 
  (ontology files, MongoDB data, etc.), and capture that location
  in the `ORR_ONT_BASE_DIR` environment variable, e.g.:

      export ORR_ONT_BASE_DIR=/opt/orr-ont-base-directory
    
- Prepare your local `orr-ont` configuration file:
  - Make a local copy, with the name `orront.conf`, of the template from 
    [here](https://raw.githubusercontent.com/mmisw/orr-ont/master/template.orront.conf)
    
        curl -o orront.conf https://raw.githubusercontent.com/mmisw/orr-ont/master/template.orront.conf
    
  - edit `./orront.conf` as needed.
    

### Run the containers

ORR's [`docker-run`](https://raw.githubusercontent.com/mmisw/orr-ont/master/bin/docker-run) 
bash script makes launching the ORR containers very straightforward.

    curl -o docker-run https://raw.githubusercontent.com/mmisw/orr-ont/master/bin/docker-run
    chmod +x docker-run
    ./docker-run mongo agraph orront

That's it!

Now open http://localhost/ont in your browser.
 

### Making your ORR instance visible to the world

This basically consists of exposing port 9090 (as indicated under the `run_orront` option in `docker-run`).
Typically you would be adjusting your main HTTP server to expose the service as desired (in term of
actual URL) via proxy or similar mechanism.
Check with your sysadmin. 
