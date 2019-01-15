#!/usr/bin/env bash
#
# Script to generate org.mmisw.orr.ont.Cfg.java from src/main/resources/orront.spec.conf.
# Generation done by the tscfg tool (https://github.com/carueda/tscfg).
#

tscfg --spec src/main/resources/orront.spec.conf \
      --pn org.mmisw.orr.ont \
      --cn Cfg \
      --scala \
      --dd src/main/scala/org/mmisw/orr/ont/
