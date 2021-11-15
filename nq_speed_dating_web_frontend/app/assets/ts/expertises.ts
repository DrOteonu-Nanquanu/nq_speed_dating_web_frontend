function save_ui_state_for_affinity(form: HTMLFormElement) {
    const {levels_of_interest, database_id, form_item_type} = get_form_data(form);
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

        if(res.status === 401) {
            window.location.href = "/?login_error=You+were+logged+out"
        }
        else if(res.status === 200) {
            set_status("Your changes are saved so that you can continue where you left off next time. They are not yet submitted to our database.");
        }
        else {
            set_status(`Failed to save changes with error ${res.status}: ${res.statusText}`);;
        }

        return res
    })
}

function get_form_data(form: HTMLFormElement) {
    const inputs = get_inputs(form);    
    const levels_of_interest = inputs
        .filter(input => input.name === "level_of_interest" && input.checked)
        .map(checkbox => checkbox.value);
    
    const database_id = get_database_id(inputs)

    const form_item_type = get_form_item_type(inputs)
    
    return {levels_of_interest, database_id, form_item_type};
}

// Query all forms on the page and submit their data to /submit_forms
function submit_all_forms() {
    const forms = get_forms();
    const forms_data = forms.map(get_form_data);
    console.log(forms_data);
    
    const empty_forms = zip(forms_data, forms)
        .filter(([{levels_of_interest}, _form]) => levels_of_interest.length === 0)
        .map(([_data, form]) => form);

    if(empty_forms.length > 0) {
        const empty_form_names = empty_forms.map(form => form.parentElement.getElementsByClassName("topic_project_name")[0].textContent)
        
        set_status("Cannot submit empty forms: " + empty_form_names.join(", "))

        return;
    }

    fetch("/submit_forms",
          {
            method: 'PUT',
            headers: {
                'Content-Type': 'text/json',
            },
            body: JSON.stringify(forms_data),
          }
     ).then(log_response)
     .then(res => {
        if(res.status === 401) {
            window.location.href = "/?login_error=You+were+logged+out"
        }
        else if(res.status === 200) {
            set_status("Succesfully submit changes to the database");
        }
        else {
            set_status(`Failed to submit with error ${res.status}: ${res.statusText}`);
        }
     });
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

function on_expertise_update(updated_checkbox: HTMLInputElement, form: HTMLFormElement) {
    disable_form_checkboxes_on_update(updated_checkbox, checkboxesOnForm(form))

    if(do_save_ui_state) {
        save_ui_state_for_affinity(form)
    }
    else {
        set_status("Your changes are not yet saved for when you log out.");
    }
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

function submit_saved_ui_state_to_kb(): Promise<Response>[] {
    set_status("Trying to submit answers...")
    const form_inputs = get_forms().map(get_inputs);

    return form_inputs.map(inputs => {
    // for(const inputs of form_inputs) {
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

        return (
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
                return res;
            })
        )
    })
}

function set_status(message: string) {
    const status_div = document.getElementById("status");
    status_div.innerText = message;
}

function disable_form_checkboxes_on_update(updated_checkbox: HTMLInputElement, all_checkboxes: HTMLInputElement[]) {
    if(updated_checkbox.checked) {
        if(checkbox_group_positive.includes(updated_checkbox.value)) {
            const no_interest = all_checkboxes.find(i => i.value === checkbox_negative);

            disable(no_interest);
        }
        else {
            all_checkboxes
                .filter(i => i.value != checkbox_negative)
                .forEach(disable);
        }
    }
    else if(all_checkboxes.findIndex(i => i.checked) === -1) {
        all_checkboxes.forEach(enable);
    }

}

function disable_form_checkboxes() {
    for(const form of get_forms()) {
        const checkboxes = checkboxesOnForm(form);

        const checked = checkboxes.filter(c => c.checked);

        if(checked.findIndex(c => checkbox_group_positive.includes(c.value)) != -1) {   // If any positive checkbox is selected, disable the negative one
            disable(checkboxes.find(i => i.value === checkbox_negative))
        }
        else if(checked.map(c => c.value).includes(checkbox_negative)) {    // If the negative checkbox is selected, disable the positive ones.
            checkboxes
                .filter(c => c.value != checkbox_negative)
                .forEach(disable)
        }
    }
}
