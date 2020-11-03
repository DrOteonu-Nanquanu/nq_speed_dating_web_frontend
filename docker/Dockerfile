FROM hseeberger/scala-sbt:8u222_1.3.5_2.13.1

WORKDIR /usr/app/

RUN apt-get update && \
      apt-get -y install sudo lsb-release bash

RUN useradd -m docker && echo "docker:docker" | chpasswd && adduser docker sudo

RUN git clone 'https://github.com/DrOteonu-Nanquanu/nq_speed_dating_web_frontend.git'

WORKDIR nq_speed_dating_web_frontend/scripts/

RUN sudo apt-get install -y build-essential
RUN bash install_postgresql

RUN curl -sL https://deb.nodesource.com/setup_10.x | sudo -E bash -
RUN bash install_typescript

WORKDIR ../nq_speed_dating_web_frontend

EXPOSE 9000

RUN sbt compile

CMD ["sbt", "run"]


