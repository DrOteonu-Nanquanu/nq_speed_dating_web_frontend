function next_page() {
    window.location.href = "/move_to_next_parent"
}

addEventListener("load", e => {
    disable_form_checkboxes()

    document.getElementById("submit").addEventListener('click', submit);

    document.getElementById("submit_and_next").addEventListener('click', e => {
        submit();
        next_page();
    });
})

