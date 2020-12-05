FROM hseeberger/scala-sbt:8u222_1.3.5_2.13.1

WORKDIR /usr/app/

RUN apt-get update && \
      apt-get -y install sudo lsb-release bash

RUN useradd -m docker && echo "docker:docker" | chpasswd && adduser docker sudo

RUN sudo apt-get install -y build-essential
RUN apt-get -y install libsodium18

RUN git clone 'https://github.com/DrOteonu-Nanquanu/nq_speed_dating_web_frontend.git'

WORKDIR nq_speed_dating_web_frontend/scripts/

RUN bash install_postgresql

RUN curl -sL https://deb.nodesource.com/setup_10.x | sudo -E bash -
RUN bash install_typescript

WORKDIR /usr/app
RUN git clone 'https://github.com/DrOteonu-Nanquanu/coin-dedureas.git'
WORKDIR coin-dedureas/Fofsequa

RUN sbt publishLocal

WORKDIR ../FofsequaReasoner
RUN bash ./eprover-init
RUN sbt publishLocal

WORKDIR /usr/app/nq_speed_dating_web_frontend/scripts
RUN pg_ctlcluster 12 main start && echo "/usr/app/coin-dedureas/FofsequaReasoner/eprover-executable/PROVER/eprover" | bash create_database

WORKDIR ../nq_speed_dating_web_frontend

EXPOSE 9000

RUN sbt compile

WORKDIR ../nq_speed_dating_web_frontend

RUN echo "pg_ctlcluster 12 main start; sbt run" > run_server

CMD bash ./run_server

# CMD ["pg_ctlcluster", "12", "main", "start"]
# RUN postgres -D /usr/local/pgsql/data

# CMD ["sbt", "run"]

# CMD ["bash"]
