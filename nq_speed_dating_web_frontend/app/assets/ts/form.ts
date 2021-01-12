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

addEventListener("load", e => {
    for(const form of Array.from(document.getElementsByTagName("form"))) {
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
})