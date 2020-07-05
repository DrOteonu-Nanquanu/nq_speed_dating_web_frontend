# Installation
## Assumptions / prerequisites
- These instructions are written with Ubuntu 18.04 as operating system in mind.
- `scripts/install_scala` uses curl. Make sure to have this installed through `sudo apt install curl`

## Running the scripts
Open a terminal in the `../scripts` folder and run the following scripts in any order (or in parallel):
- `bash install_postgresql`
- `bash install_scala`
- `bash install_typescript`

Lastly, run `bash create_database`.

To then run the web server, open a terminal in the folder where this README is located, and execute `sbt run`. Then visit `localhost:9000` to view the website.