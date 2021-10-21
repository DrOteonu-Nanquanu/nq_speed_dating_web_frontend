# Nanquanu Speed Dating Web Frontend

Source code of the application can be found in ./nq_speed_dating_web_frontend

## Docker

To use the `docker-compose.yaml`, make sure you build the scala app docker image first.
You can achieve this by executing `sbt docker:publishLocal`.

Then simply execute `docker-compose up -d` to run it locally, or add `--context` with your context to deploy remotely.

//TODO automate building of docker image in a pipeline (github actions)
