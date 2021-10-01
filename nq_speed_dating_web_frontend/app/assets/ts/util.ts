
function log_response(response: Response) {
    console.log(response)
    response.text().then(console.log)

    return response
}


function log_return(message: any) {
    console.log(message);
    return message;
}

function checkboxesOnForm(form: HTMLFormElement) {
    return ((
        Array.from(
            form.getElementsByTagName("input")
        ) as HTMLInputElement[])
        .filter(i => i.type === "checkbox")
    );
}

function get_forms(): HTMLFormElement[] {
    return Array.from(document.getElementsByTagName("form"))
}

function get_inputs(form: HTMLFormElement) {
    return Array.from(form.getElementsByTagName("input")) as HTMLInputElement[]
}
