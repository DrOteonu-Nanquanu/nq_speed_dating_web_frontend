function submit_expertise(form: HTMLFormElement) {
    console.log(form);

    const inputs = Array.from(form.getElementsByTagName("input")) as HTMLInputElement[];    
    const level_of_interest = inputs
        .filter(input => input.name === "level_of_interest")
        .find(radio_button => radio_button.checked)
        .value;
    
    const expertise_id = parseInt(inputs
        .find(input => input.id="expertise_id")
        .value);

    console.log(level_of_interest, expertise_id);
    fetch(`../expertise/update`,
        {
            method: 'PUT',
            headers: {
                'Content-Type': 'text/json',
            },
            body: JSON.stringify({
                expertise_id, level_of_interest
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

function log_return(message) {
    console.log(message);
    return message;
}
