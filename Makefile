IMAGE=gcr.io/camunda-researchanddevelopment/falko-camunda-8-benchmark:0.0.1-SNAPSHOT

all:
	docker build . --tag $(IMAGE)

install:
	docker push $(IMAGE)
