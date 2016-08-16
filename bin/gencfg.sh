#!/usr/bin/env bash
#
# Script to generate org.mmisw.orr.ont.Cfg.java from src/main/resources/orront.spec.conf.
# Generation done by the tscfg tool (https://github.com/carueda/tscfg).
# Although generated, Cfg.java is at the moment under version control for convenience but
# also because the tscfg tool doesn't yet provide a mechanism to generate the source as part
# of the build setup in client projects.
#

tscfg --spec src/main/resources/orront.spec.conf \
      --pn org.mmisw.orr.ont \
      --cn Cfg \
      --scala \
      --dd src/main/scala/org/mmisw/orr/ont/
