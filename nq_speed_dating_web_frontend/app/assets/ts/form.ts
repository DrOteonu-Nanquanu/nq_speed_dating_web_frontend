function log_response(response: Response) {
    console.log(response)
    response.text().then(console.log)

    return response
}

function submit_expertise(form: HTMLFormElement) {
    console.log(form);

    const inputs = get_inputs(form);    
    const levels_of_interest = inputs
        .filter(input => input.name === "level_of_interest" && input.checked)
        .map(checkbox => checkbox.value);
    
    const database_id = get_database_id(inputs)

    const form_item_type = get_form_item_type(inputs)

    console.log(levels_of_interest, database_id);
    fetch("/update_form_item",
        {
            method: 'PUT',
            headers: {
                'Content-Type': 'text/json',
            },
            body: JSON.stringify({
                database_id, levels_of_interest: levels_of_interest, form_item_type
            }),
        }
    ).then(res => {
        console.log(res)
        res.text().then(console.log)

        if(res.status == 401) {
            window.location.href = "/"
        }

        return res
    })
}

function enable(checkbox: HTMLInputElement) {
    checkbox.disabled = false;
    checkbox.parentElement.style.color = "";
}

function disable(checkbox: HTMLInputElement) {
    checkbox.checked = false;
    checkbox.disabled = true;

    checkbox.parentElement.style.color = "grey";
}

const checkbox_group_positive = ["some_expertise", "interested", "sympathise"];
const checkbox_negative = "no_interest"

function on_expertise_update(updated: HTMLInputElement, form: HTMLFormElement) {
    const checkboxes = checkboxesOnForm(form);
    
    if(updated.checked) {
        if(checkbox_group_positive.includes(updated.value)) {
            const no_interest = checkboxes.find(i => i.value == checkbox_negative);

            disable(no_interest);
        }
        else {
            checkboxes
                .filter(i => i.value != checkbox_negative)
                .forEach(disable);
        }
    }
    else if(checkboxes.findIndex(i => i.checked) == -1) {
        checkboxes.forEach(enable);
    }

    submit_expertise(form)

    set_status("Your changes are saved but not yet submitted.");
}

function log_return(message) {
    console.log(message);
    return message;
}

function checkboxesOnForm(form: HTMLFormElement) {
    return ((
        Array.from(
            form.getElementsByTagName("input")
        ) as HTMLInputElement[])
        .filter(i => i.type == "checkbox")
    );
}

function get_forms(): HTMLFormElement[] {
    return Array.from(document.getElementsByTagName("form"))
}

function get_database_id(inputs: HTMLInputElement[]) {
    return parseInt(
        inputs
            .find(input => input.id="database_id")
            .value
    );
}

function get_form_item_type(inputs: HTMLInputElement[]) {
    return (inputs
        .find(input => input.id === "form_item_type")
        .value
   );
}

function get_inputs(form: HTMLFormElement) {
    return Array.from(form.getElementsByTagName("input")) as HTMLInputElement[]
}

function submit() {
    set_status("Trying to submit answers...")
    const form_inputs = get_forms().map(get_inputs);

    for(const inputs of form_inputs) {
        const topic_id = get_database_id(inputs);
        const type = get_form_item_type(inputs);

        let submit_type_name: string;
        if(type === 'project') {
            submit_type_name = 'project';
        }
        else if (type === 'expertise') {
            submit_type_name = 'topic';
        }
        else {
            throw Error('unknown form_item_type: ' + type);
        }

        fetch(`/submit_${submit_type_name}/` + topic_id, {
            method: 'POST'
        })
        .then(log_response)
        .then(res => {
            if(res.ok){
                set_status("Succesfully submitted");
            }
            else {
                set_status(`Failed to submit with error ${res.status}: ${res.statusText}`);
            }
        });
    }
}

function next_page() {
    window.location.href = "/move_to_next_parent"
}

addEventListener("load", e => {
    for(const form of get_forms()) {
        const checkboxes = checkboxesOnForm(form);

        const checked = checkboxes.filter(c => c.checked);

        if(checked.findIndex(c => checkbox_group_positive.includes(c.value)) != -1) {   // If any positive checkbox is selected, disable the negative one
            disable(checkboxes.find(i => i.value == checkbox_negative))
        }
        else if(checked.map(c => c.value).includes(checkbox_negative)) {    // If the negative checkbox is selected, disable the positive ones.
            checkboxes
                .filter(c => c.value != checkbox_negative)
                .forEach(disable)
        }
    }

    document.getElementById("submit").addEventListener('click', submit);

    document.getElementById("submit_and_next").addEventListener('click', e => {
        submit();
        next_page();
    });
})


function set_status(message: string) {
    const status_div = document.getElementById("status");
    status_div.innerText = message;
}
