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

- Create a directory on your host machine as a base location for all
  configuration files and data (ontology files, MongoDB data, etc.),
  and capture that location in the `ORR_ONT_BASE_DIR` environment variable, e.g.:

      export ORR_ONT_BASE_DIR=/opt/orr-ont-base-directory
    
- Navigate to `$ORR_ONT_BASE_DIR`:

      cd $ORR_ONT_BASE_DIR
    
- Prepare your local "orr-ont" configuration file for the back-end service:
  - Make a local copy, with the name `orront.conf`, of
    [template.orront.conf](https://raw.githubusercontent.com/mmisw/orr-ont/master/template.orront.conf)
    
        curl -o orront.conf https://raw.githubusercontent.com/mmisw/orr-ont/master/template.orront.conf
    
  - edit `./orront.conf` as needed.
    Note that some settings there interplay with corresponding settings in the "orr-portal" configuration
    indicated below.
    
- Prepare your local "orr-portal" configuration file for the frontend:
  - Make a local copy, with the name `local.config.js`, of
    [template.local.config.js](https://raw.githubusercontent.com/mmisw/orr-portal/master/template.local.config.js)
    
        curl -o local.config.js https://raw.githubusercontent.com/mmisw/orr-portal/master/template.local.config.js
    
  - edit `./local.config.js` as needed.
    

### Run the containers

ORR's [`docker-run`](https://raw.githubusercontent.com/mmisw/orr-ont/master/bin/docker-run) 
bash script makes launching the ORR containers very straightforward.

    curl -o docker-run https://raw.githubusercontent.com/mmisw/orr-ont/master/bin/docker-run
    chmod +x docker-run
    ./docker-run mongo agraph orront

That's it!

Now open http://localhost/ont in your browser.

Please note, this is a minimal setup for running your ORR instance.
Of course, there are several aspects to consider and put in place toward a production
environment including making your ORR instance visible to the world (se below),
re-starting containers, reflecting image updates, logging, backups, etc.
Please check with your sysadmin.
 

### Making your ORR instance visible to the world

This basically consists of exposing port 9090 (as indicated under the `run_orront` option in `docker-run`).
Typically you would be adjusting your main HTTP server to expose the service as desired (in terms of
actual URL) via proxy or similar mechanism.
Check with your sysadmin. 
