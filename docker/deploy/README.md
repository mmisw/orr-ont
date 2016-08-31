# ORR Deployment via Docker

The Docker images required to run the ORR system are:

| Image                                       |  Purpose |
|---------------------------------------------|---------------------------|
| [`mongo`](https://hub.docker.com/_/mongo/)  | MongoDB used for all data |
| [`franzinc/agraph`](https://hub.docker.com/r/franzinc/agraph/) | AllegroGraph server and SPARQL endpoint |
| [`mmisw/orr-ont`](https://hub.docker.com/r/mmisw/orr-ont/)   | The ORR System itself |

The user performing the deployment should have the relevant Docker privileges.


A complete deployment of the ORR, including the required supporting services,
consists of the following steps.

- Create a directory on your host machine as a base location for all
  configuration files and data (ontology files, MongoDB data, etc.).

- Create these subdirectories:

        $ mkdir mongo_data
        $ mkdir orr_data
    
- Get these files:

        $ curl -o docker-compose.yml https://raw.githubusercontent.com/mmisw/orr-ont/master/docker/deploy/docker-compose.yml
        $ curl -o orront.conf https://raw.githubusercontent.com/mmisw/orr-ont/master/docker/deploy/orront.conf
        $ curl -o local.config.js https://raw.githubusercontent.com/mmisw/orr-ont/master/docker/deploy/local.config.js

- To specify a list of email address that should be notified whenever there's a
user, organization, or ontology registration, create a `notifyemails` text file and put each
email address on a line by itself, e.g.,:

        $ cat > notifyemails
        foo@example.net
        other@example.net
        ^D

- Edit the downloaded files as indicated.

- Launch the ORR:

        $ docker-compose up -d
        Starting agraph
        Starting mongo
        Starting orr-ont
    
- Copy `local.config.js` to the `orr-ont` container:

        $ docker cp local.config.js  orr-ont:/usr/local/tomcat/webapps/ont/js/
    
- Open the ORR in your browser. For example, assuming 9090 is the associated host port,
  you can now open [http://localhost:9090/ont/](http://localhost:9090/ont/).
  You can login with the username "admin" and the password indicated in `orront.conf`.
 

- To shutdown the whole ORR:

        $ docker-compose down
        Stopping orr-ont ... done
        Stopping mongo ... done
        Stopping agraph ... done
        Removing orr-ont ... done
        Removing mongo ... done
        Removing agraph ... done


Please note: The above Docker set-up should in general be complemented with appropriate
mechanism toward a production environment.
Aspects to consider include:
making your ORR instance externally visible, re-starting the containers to reflect configuration
and image updates, logging, backups, etc.
Please check with your sysadmin.

TODO: illustrate typical Apache HTTPD proxy configuration to expose the ORR itself and the
SPARQL endpoint.
