function submit_expertise(form: HTMLFormElement) {
    console.log(form);

    const inputs = Array.from(form.getElementsByTagName("input")) as HTMLInputElement[];    
    const level_of_interest = inputs
        .filter(input => input.name === "level_of_interest")
        .find(radio_button => radio_button.checked)
        .value;
    
    const expertise_id = inputs
        .find(input => input.id="expertise_id")
        .value;

    console.log(level_of_interest, expertise_id);
    fetch(`../expertise/update/exp_id=${expertise_id}&new_level=${level_of_interest}`,
        {
            method: 'PUT',
        }
    ).then(res => {
        console.log(res)
        res.text().then(console.log)
        return res
    })
}

function log_return(message) {
    console.log(message);
    return message;
}