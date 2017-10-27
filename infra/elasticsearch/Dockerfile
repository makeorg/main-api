FROM docker.elastic.co/elasticsearch/elasticsearch:5.6.3

USER elasticsearch
RUN bin/elasticsearch-plugin install -b analysis-icu || true

