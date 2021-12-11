FROM openjdk:11-slim-bullseye

ADD https://github.com/sbt/sbt/releases/download/v1.5.6/sbt-1.5.6.tgz .

RUN tar xf sbt-1.5.6.tgz && cp -r sbt/bin .

WORKDIR /build

ADD nq_speed_dating_web_frontend .
RUN sbt dist

RUN ls target

ENTRYPOINT [ "/bin/sh" ]