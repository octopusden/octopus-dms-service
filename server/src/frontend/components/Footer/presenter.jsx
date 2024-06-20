import React from "react";
import './style.css'
import AdminPane from "../AdminPane";
import {hasPermission} from "../common";
import get from "lodash/get";

export default function footer(props) {
    const {buildInfo, loggedUser} = props
    const version = get(buildInfo, "build.version", "dev")
    return <div className="footer-wrapper">
        <div className="version-wrapper">DMS Portal by F1 team ({version})</div>
        {hasPermission(loggedUser, "DELETE_DATA") &&
            <AdminPane/>
        }
    </div>
}