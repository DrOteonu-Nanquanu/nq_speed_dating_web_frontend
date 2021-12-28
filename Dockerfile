FROM openjdk:11-slim-bullseye AS build

ADD https://github.com/sbt/sbt/releases/download/v1.5.6/sbt-1.5.6.tgz .

RUN tar xf sbt-1.5.6.tgz && cp -r sbt/bin .
RUN apt-get update && apt-get install nodejs unzip -y

WORKDIR /build

ADD nq_speed_dating_web_frontend .
RUN sbt dist

WORKDIR /build/target/universal

RUN unzip -o nq_speed_dating_web_frontend-0.1-SNAPSHOT.zip && mv nq_speed_dating_web_frontend-0.1-SNAPSHOT app && tar cf app.tar.gz app

FROM openjdk:11-jre-slim

COPY --from=build /build/target/universal/app.tar.gz .

RUN tar xf app.tar.gz

WORKDIR /app

ENTRYPOINT [ "bin/nq_speed_dating_web_frontend" ]