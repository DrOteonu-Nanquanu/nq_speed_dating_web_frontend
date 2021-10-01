var do_save_ui_state = true;

function next_page() {
    window.location.href = "/move_to_next_parent"
}

addEventListener("load", e => {
    disable_form_checkboxes()

    document.getElementById("submit").addEventListener('click', submit_saved_ui_state_to_kb);

    document.getElementById("submit_and_next").addEventListener('click', e => {
        Promise.all(submit_saved_ui_state_to_kb()).then(next_page)
    });
})

