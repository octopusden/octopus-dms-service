import React from "react";
import './style.css'
import AdminPane from "../AdminPane";
import {hasPermission} from "../common";

export default function footer(props) {
    const {loggedUser, version} = props
    return <div className="footer-wrapper">
        <div className="version-wrapper">DMS Portal {version} by F1 team</div>
        {hasPermission(loggedUser, "DELETE_DATA") &&
        <AdminPane/>
        }
    </div>
}