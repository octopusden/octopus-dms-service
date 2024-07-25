import {Button} from "@blueprintjs/core";
import React from "react";
import './style.css'

export default function header(props) {
    const {username, logOut} = props
    return <div className="header-wrapper">
        <div className='logo'>DMS Portal</div>
        <div className='greetings-wrapper'>
            <div className='greetings'>
                Hello, {username}!
            </div>
            <Button onClick={logOut}
                    className='logout-button'
                    icon='log-out'
                    large minimal/>
        </div>
    </div>
}