# Installation
## Assumptions / prerequisites
- You should obviously first clone this git repository to your machine.
- These instructions are written with Ubuntu 18.04 as operating system in mind.
- `scripts/install_scala` uses curl. Make sure to have this installed through `sudo apt install curl`

This project depends on Fofsequa and FofsequaReasoner. Clone https://github.com/DrOteonu-Nanquanu/coin-dedureas and follow the installation instructions for these two projects. Make sure you have also run `sbt publishLocal` on FofsequaReasoner. Since fofsequa\_sql uses it as a library.

## Running the scripts
Open a terminal in the `../scripts` folder and run the following scripts in any order:
- `bash install_postgresql`
- `bash install_scala`
- `bash install_typescript`

Lastly, run `bash create_database`.

To then run the web server, open a terminal in the folder where this README is located, and execute `sbt run`. Then visit `localhost:9000` to view the website.
