#!/usr/bin/env python3
#
# An ad hoc script to help admins with some registry maintenance, for example,
# to adjust the visibility and status of testing or irrelevant registrations.
# Just basic testing of this script has been performed upon putting it together,
# but it should functional in general.
#
# Requirements:
#   pip3 install requests
#
# This script uses the ORR API, see https://mmisw.org/orrdoc/api/.
# Some settings (credentials) are given via env vars, and others in the script itself.
# Adjust anything in this script as needed.
#
# NOTE If you are an admin, REMEMBER: with great power comes great responsibility!
# You could mess up things in the registry if you are not careful enough with the
# target IRIs/versions that you want to modify or remove.

# The particular ORR API endpoint:
ORR_API = "https://mmisw.org/ont/api/v0"

# For regular processing commands, have your your credentials
# captured in these env vars:
#     export ORR_USERNAME=myusername
#     export ORR_TOKEN=mytoken
# If you don't know your token, run:
#    ./misc.py login myusername 'mypasswd'

# For convenience, you can do some testing of the ontology update/removal
# functionality against some appropriate entry for that purpose. Example:
TEST_ONT_IRI = "https://mmisw.org/ont/mmitest/testtest"
TEST_ONT_VERSION = "20210501T202157"
# The possible commands against the above are:
#    ./misc.py test-update
#    ./misc.py test-delete
#
# TIP: you can see extended metadata of a particular entry via the
# special `!md` format. For example, open this in your browser:
#    https://mmisw.org/ont/mmitest/testtest?format=!md

import sys
import os
import requests

# Prints out the token if valid credentials are given
def login(username, password):
    url = "{}/user/auth".format(ORR_API)
    json=dict(username=username, password=password)

    r = requests.post(url, json=json)
    if r.status_code == 200:
        print(r.json()["token"])
    else:
        print("{}: {}".format(r.status_code, r.text))

def env_var(name):
    v = os.getenv(name)
    if v:
        return v
    else:
        print("undefined env var {}".format(name))
        sys.exit(1)

def username():
    return env_var("ORR_USERNAME")

def token():
    return env_var("ORR_TOKEN")

def headers():
    return dict(Authorization="Bearer {}".format(token()))

# Updates a particular ontology version
def update_ont_version(iri, version):
    print("updating iri={}, version={}".format(iri, version))
    url = "{}/ont".format(ORR_API)
    json=dict(iri=iri,
              version=version,
              userName=username(),

              # adjust any of the following as needed:
              visibility="owner",
              status="testing"
              )
    r = requests.put(url, json=json, headers=headers())
    print(r.json())

# Deletes an ontology version if `version` is not None;
# otherwise, the complete ontology entry (all versions).
# USE WITH CARE!
def delete_ont(iri, version):
    print("deleting iri={}, version={}".format(iri, version))
    url = "{}/ont".format(ORR_API)
    json=dict(iri=iri)
    if version:
        json["version"] = version
    r = requests.delete(url, json=json, headers=headers())
    print(r.json())

def test_update():
    update_ont_version(TEST_ONT_IRI, TEST_ONT_VERSION)

def test_delete():
    delete_ont(TEST_ONT_IRI, TEST_ONT_VERSION)

def list_onts():
    print("retrieving list of ontologies")
    url = "{}/ont".format(ORR_API)
    r = requests.get(url, headers=headers())
    return r.json()

# Condition to select a particular ontology.
# Of course, adjust as needed.
def select(ont):
    return ont["visibility"] == "public" and \
        "test" in ont["name"]

def get_selected_onts():
    onts = list_onts()
    print("   total: {} ontologies".format(len(onts)))
    selected_onts = [ont for ont in onts if select(ont)]
    print("selected: {} ontologies:".format(len(selected_onts)))
    return selected_onts

# Shows some key pieces of an ontology entry
def show_ont(ont):
    print("     name    : {}".format(ont["name"]))
    print("     iri     : {}".format(ont["uri"]))
    print("     version : {}".format(ont["version"]))
    print("   ownerName : {}".format(ont.get("ownerName", "?")))
    print("  visibility : {}".format(ont.get("visibility", "?")))
    print("      status : {}".format(ont.get("status", "?")))
    print()

def show_selected_onts():
    onts = get_selected_onts()
    for ont in onts:
        show_ont(ont)

def process_ont(ont):
    print("TODO process_ont: ont.iri={}".format(ont["uri"]))

    # decide what to do here with the ont, eg:

    # update some attributes:
    #update_ont_version(ont["uri"], ont["version"])

    # delete the particular version:
    #delete_ont(ont["uri"], ont["version"])

    # delete the whole ontology entry:
    #delete_ont(ont["uri"], None)

def process_selected_onts():
    onts = get_selected_onts()
    for ont in onts:
        process_ont(ont)

def main():
    usage = """Usage:
    ./misc.py login u p
    ./misc.py test-update
    ./misc.py test-delete
    ./misc.py show-selected
    ./misc.py process-selected
    """
    if len(sys.argv) < 2:
        print(usage)
    elif sys.argv[1] == "login":
        if len(sys.argv) < 4:
            login(*sys.argv[2:4])
        else:
            print("missing arguments")
    elif sys.argv[1] == "test-update":
        test_update()
    elif sys.argv[1] == "test-delete":
        test_delete()
    elif sys.argv[1] == "show-selected":
        show_selected_onts()
    elif sys.argv[1] == "process-selected":
        process_selected_onts()
    else:
        print(usage)


if __name__ == "__main__":
    main()
