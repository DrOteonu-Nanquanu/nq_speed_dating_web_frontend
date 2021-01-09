function submit_expertise(form: HTMLFormElement) {
    console.log(form);

    const inputs = Array.from(form.getElementsByTagName("input")) as HTMLInputElement[];    
    const levels_of_interest = inputs
        .filter(input => input.name === "level_of_interest" && input.checked)
        .map(checkbox => checkbox.value);
    
    const database_id = parseInt(inputs
        .find(input => input.id="database_id")
        .value);

    const form_item_type = inputs
        .find(input => input.id === "form_item_type")
        .value

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

function on_expertise_update(updated: HTMLInputElement, form: HTMLFormElement) {
    const checkboxes = (
        Array.from(
            form.getElementsByTagName("input")
        ) as HTMLInputElement[])
        .filter(i => i.type == "checkbox");
    
    if(updated.checked) {
        if(["some_expertise", "interested", "sympathise"].includes(updated.value)) {
            const no_interest = checkboxes.find(i => i.value == "no_interest");

            disable(no_interest);
        }
        else {
            checkboxes
                .filter(i => i.value != "no_interest")
                .forEach(disable);
        }
    }
    else if(checkboxes.findIndex(i => i.checked) == -1) {
        checkboxes.forEach(enable);
    }

    submit_expertise(form)
}

function log_return(message) {
    console.log(message);
    return message;
}
