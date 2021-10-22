var do_save_ui_state = false;

addEventListener("load", e => {
    disable_form_checkboxes();

    document.getElementById("submit").addEventListener('click', submit_all_forms);
})

